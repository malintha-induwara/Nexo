package com.nexo.app.ui.entry

import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.common.period.toLocalDateTime
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeDraft
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatRule
import com.nexo.app.core.model.RepeatSchedule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxDraft
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.repository.FavoritesRepository
import com.nexo.app.data.repository.PhotosRepository
import com.nexo.app.data.repository.ReceiptRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TagResult
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.data.repository.TransactionImportRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.noteFor
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.sms.FeePlan
import com.nexo.app.domain.sms.GetSmsFeePlanUseCase
import com.nexo.app.domain.sms.GetSmsLedgerAmountUseCase
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.UserMessage
import com.nexo.app.ui.designsystem.component.PadInput
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.sms.rateFromApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = EntryViewModel.Factory::class)
class EntryViewModel @AssistedInject constructor(
    @Assisted private val key: EntryKey,
    private val refs: ReferenceDataRepository,
    private val smsRepository: SmsRepository,
    private val transactions: TransactionsRepository,
    private val favorites: FavoritesRepository,
    private val repeats: RepeatRulesRepository,
    private val prefs: UserPreferencesRepository,
    private val home: HomeState,
    private val photos: PhotosRepository,
    private val tags: TagsRepository,
    private val importer: TransactionImportRepository,
    private val getSmsLedgerAmount: GetSmsLedgerAmountUseCase,
    private val getSmsFeePlan: GetSmsFeePlanUseCase,
    private val receipts: ReceiptRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: EntryKey): EntryViewModel
    }

    private val form = MutableStateFlow(EntryForm())

    /** Kept for event handlers that need the reference data synchronously. */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())

    private val suggestions = MutableStateFlow<List<NoteSuggestion>>(emptyList())

    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val navigation = MutableStateFlow<EntryNavigation?>(null)

    val uiState: StateFlow<EntryUiState> = combine(
        combine(form, ref, prefs.getDeviceSettingsStream(), ::Triple),
        combine(suggestions, tags.getTagsStream(), ::Pair),
        combine(userMessage, navigation, ::Pair),
    ) { (form, r, device), (suggestions, tagList), (message, navigation) ->
        EntryUiState(form, r, device, suggestions, tagList, message, navigation)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState())

    private var texts = EntryTexts(paymentNote = "")
    private var initialized = false

    private var originalState: EntryForm? = null
    private var original: Transaction? = null
    private var originalRule: RepeatRule? = null

    /** Loads the form once; safe to call again. [texts] are the strings the form needs as data. */
    @MainThread
    fun initialize(texts: EntryTexts) {
        if (initialized) return
        initialized = true
        this.texts = texts
        home.pickedFavoriteConsumed()
        viewModelScope.launch {
            val r = refs.getRefData()
            val base = EntryForm(inputOrder = r.settings.inputOrder, currencyId = r.mainCurrency.id, feeCurrencyId = r.mainCurrency.id)
            val loaded = when (key.mode) {
                EntryMode.EDIT -> loadExisting(r, base, copy = false).let { s -> key.txId?.let { withFxNote(s, it, r) } ?: s }
                EntryMode.COPY -> loadExisting(r, base, copy = true)
                EntryMode.FAVORITE -> applyFavorite(r, base.copy(mode = EntryMode.FAVORITE), key.favoriteId)
                EntryMode.REPEAT_EDIT -> loadRule(r, base)
                EntryMode.PAY_CARD -> loadPayCard(r, base)
                EntryMode.IMPORT_FIX -> loadImportRow(r, base)
                EntryMode.SMS -> loadSmsItem(r, base)
                EntryMode.REPEAT_NEW -> newEntry(r, base).copy(mode = EntryMode.REPEAT_NEW, repeat = key.repeat ?: RepeatType.MONTHLY)
                else -> newEntry(r, base)
            }
            val unassigned = if (key.reviewUnassigned) transactions.getUnassigned(loaded.date.toIsoText()).size else 0
            form.value = loaded.copy(loaded = true, unassignedCount = unassigned)
            originalState = form.value
            if (!loaded.viewing && key.mode != EntryMode.PAY_CARD) advance()
            loadSuggestions(r)
        }
        viewModelScope.launch {
            home.pickedFavorite.filterNotNull().collect { id ->
                home.pickedFavoriteConsumed()
                form.value = applyFavorite(refs.getRefData(), form.value, id, keepDate = true)
            }
        }
    }

    fun onUserMessageShown(id: Long) {
        userMessage.update { if (it?.id == id) null else it }
    }

    fun onNavigationHandled() {
        navigation.value = null
    }

    private fun showMessage(@StringRes text: Int, arg: Int? = null) {
        userMessage.value = UserMessage(UiText.Resource(text, listOfNotNull(arg)))
    }

    /** Leaves the form; with [focusDate] the Transactions tab scrolls there. */
    private fun close(focusDate: LocalDate?) {
        focusDate?.let(home::focusOn)
        navigation.value = EntryNavigation.Close
    }

    private fun open(key: EntryKey, replace: Boolean = true) {
        navigation.value = EntryNavigation.Open(key, replace)
    }

    fun isPhotoSupported(uri: Uri): Boolean = photos.isSupported(uri)

    fun photoFile(fileName: String): File = photos.getPhotoFile(fileName)

    suspend fun exportPhoto(fileName: String): Boolean = photos.exportToGallery(fileName)

    suspend fun addTag(raw: String): TagResult = tags.add(raw)

    suspend fun renameTag(id: String, raw: String): TagResult = tags.rename(id, raw)

    suspend fun tagUsageCount(id: String): Int = tags.usageCount(id)

    suspend fun deleteTag(id: String) = tags.delete(id)

    suspend fun reorderTags(ids: List<String>) = tags.reorder(ids)

    private suspend fun loadSuggestions(r: RefData) {
        if (!r.settings.autocomplete) return
        val cutoff = maxOf(System.currentTimeMillis() - 456L * 24 * 3600 * 1000, r.settings.autocompleteResetAt)
        val cutDate = Instant.ofEpochMilli(cutoff).atZone(ZoneId.systemDefault()).toLocalDate().toIsoText()
        suggestions.value = transactions.getNoteUsage(cutDate, cutoff).map { NoteSuggestion(it.note, it.count, it.lastDate) }
    }

    private fun newEntry(r: RefData, base: EntryForm): EntryForm {
        val date = key.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        var s = base.copy(mode = key.mode, date = date, type = key.type ?: EntryType.EXPENSE, sourceText = key.sourceText)
        if (key.accountId != null && r.accountById.containsKey(key.accountId)) {
            s = s.copy(accountId = key.accountId, presetAccountId = key.accountId, currencyId = r.accountCurrency(key.accountId).id, feeCurrencyId = r.accountCurrency(key.accountId).id)
        } else if (r.pickerAccounts.size == 1) {
            val only = r.pickerAccounts.first()
            s = s.copy(accountId = only.id, currencyId = only.currencyId, feeCurrencyId = only.currencyId)
        }
        if (key.categoryId != null) {
            r.categoryById[key.categoryId]?.let { c ->
                s = s.copy(categoryId = c.id, presetCategoryId = c.id, type = if (c.kind == CategoryKind.INCOME) EntryType.INCOME else EntryType.EXPENSE)
            }
        }
        return s
    }

    private suspend fun loadExisting(r: RefData, base: EntryForm, copy: Boolean): EntryForm {
        var tx = key.txId?.let { transactions.getTransaction(it) } ?: return newEntry(r, base)
        if (tx.kind == TxKind.TRANSFER_IN) {
            val out = tx.transferGroupId?.let { g -> transactions.getTransferLegs(g).firstOrNull { it.kind == TxKind.TRANSFER_OUT } }
            tx = out ?: tx.copy(kind = TxKind.TRANSFER_OUT, accountId = tx.counterpartAccountId, counterpartAccountId = tx.accountId)
        }
        original = if (copy) null else tx
        val dateTime = tx.dateTime.toLocalDateTime()
        val storedDate = runCatching { LocalDate.parse(tx.date) }.getOrDefault(dateTime.toLocalDate())
        val type = when (tx.kind) {
            TxKind.INCOME, TxKind.BALANCE_INCREASE -> EntryType.INCOME
            TxKind.EXPENSE, TxKind.BALANCE_DECREASE -> EntryType.EXPENSE
            else -> EntryType.TRANSFER
        }
        val fee = if (tx.kind.isTransfer) tx.feeGroupId?.let { transactions.getFeeRow(it) } else null
        val tags = transactions.getTagIds(tx.id)
        val photos = if (copy) emptyList() else transactions.getPhotos(tx.id).map { PhotoItem.Stored(it.fileName, it.checksum, it.fileSize) }
        val rate = if (tx.currencyId != r.mainCurrency.id && tx.amount != 0.0) {
            BigDecimal.valueOf(tx.amountMain).divide(BigDecimal.valueOf(tx.amount), 10, RoundingMode.HALF_UP).toDouble()
        } else null
        val installment = if (!copy && tx.installmentGroupId != null) {
            val group = transactions.getInstallmentGroup(tx.installmentGroupId).filter { it.kind != TxKind.TRANSFER_IN }
            val total = Money.sum(group.map { it.amount })
            val after = group.filter { it.date > tx.date || (it.date == tx.date && it.dateTime > tx.dateTime) }
            InstallmentInfo(tx.installmentIndex, tx.installmentCount, Money.sum(after.map { it.amount }), total)
        } else null
        val copyDate = if (copy && key.copyWithToday) LocalDate.now() else storedDate
        val copyTime = if (copy && key.copyWithToday) LocalTime.now().withSecond(0).withNano(0) else dateTime.toLocalTime()
        return base.copy(
            mode = if (copy) EntryMode.NEW else EntryMode.EDIT,
            viewing = !copy,
            type = type,
            balanceKind = if (tx.kind.isBalance) tx.kind else null,
            isBalanceAdjustment = tx.isBalanceAdjustment || tx.kind.isBalance,
            date = copyDate,
            time = copyTime,
            accountId = tx.accountId,
            toAccountId = if (tx.kind.isTransfer) tx.counterpartAccountId else null,
            categoryId = tx.categoryId,
            amount = tx.amount,
            currencyId = tx.currencyId,
            rate = rate,
            note = tx.note,
            description = tx.description,
            tagIds = tags,
            photos = photos,
            feeExpanded = fee != null,
            feeAmount = fee?.amount,
            feeCurrencyId = fee?.currencyId ?: tx.currencyId,
            feeLoaded = true,
            originalId = if (copy) null else tx.id,
            installmentInfo = installment,
            sourceText = if (copy) null else tx.sourceText,
        )
    }

    private suspend fun applyFavorite(r: RefData, base: EntryForm, favoriteId: String?, keepDate: Boolean = false): EntryForm {
        val fav = favoriteId?.let { favorites.getFavorite(it) } ?: return base
        val transfer = fav.kind.isTransfer
        val from = if (fav.kind == TxKind.TRANSFER_IN) fav.toAccountId else fav.accountId
        val to = if (fav.kind == TxKind.TRANSFER_IN) fav.accountId else fav.toAccountId
        var s = base.copy(
            type = when {
                transfer -> EntryType.TRANSFER
                fav.kind == TxKind.INCOME -> EntryType.INCOME
                else -> EntryType.EXPENSE
            },
            accountId = from?.takeIf { r.accountById.containsKey(it) },
            toAccountId = if (transfer) to else null,
            categoryId = if (transfer) null else fav.categoryId?.takeIf { r.categoryById[it]?.isDeleted == false },
            amount = fav.amount,
            currencyId = r.currency(fav.currencyId).id,
            rate = null,
            note = fav.note,
            description = fav.description,
            balanceKind = null,
            isBalanceAdjustment = false,
            feeExpanded = false,
            feeAmount = null,
            feeLoaded = false,
            installments = 0,
        )
        if (!keepDate) s = s.copy(date = LocalDate.now(), time = LocalTime.now().withSecond(0).withNano(0))
        if (transfer) s = loadLastFee(s)
        return s
    }

    private suspend fun loadRule(r: RefData, base: EntryForm): EntryForm {
        val rule = key.repeatRuleId?.let { repeats.getRule(it) } ?: return newEntry(r, base)
        originalRule = rule
        return base.copy(
            mode = EntryMode.REPEAT_EDIT,
            type = when (rule.kind) {
                TxKind.INCOME -> EntryType.INCOME
                TxKind.TRANSFER_OUT -> EntryType.TRANSFER
                else -> EntryType.EXPENSE
            },
            date = LocalDate.parse(rule.nextDate),
            accountId = rule.accountId,
            toAccountId = rule.toAccountId,
            categoryId = rule.categoryId,
            amount = rule.amount,
            currencyId = r.currency(rule.currencyId).id,
            note = rule.note,
            description = rule.description,
            repeat = rule.repeatType,
            repeatEnd = rule.endDate?.let(LocalDate::parse),
        )
    }

    private suspend fun loadPayCard(r: RefData, base: EntryForm): EntryForm {
        val card = key.accountId?.let { r.accountById[it] } ?: return newEntry(r, base)
        val ids = listOf(card.id) + r.linkedDebitCards(card.id).map { it.id }
        val figures = CardStatement.compute(r, card, transactions.getAllForAccounts(ids), LocalDate.now())
        val payer = card.linkedAccountId?.takeIf { id -> r.accountById[id]?.let { r.isAccountVisible(it) } == true }
        return base.copy(
            mode = EntryMode.PAY_CARD,
            type = EntryType.TRANSFER,
            accountId = payer,
            toAccountId = card.id,
            currencyId = card.currencyId,
            feeCurrencyId = card.currencyId,
            amount = figures.payAmount,
            note = texts.paymentNote,
        )
    }

    /** The Review Queue item being reviewed in [EntryMode.SMS]. */
    private var smsItem: SmsItem? = null

    /** The form opens with everything the SMS gave; saving approves the item. */
    private suspend fun loadSmsItem(r: RefData, base: EntryForm): EntryForm {
        val item = key.smsItemId?.let { smsRepository.getItem(it) } ?: return newEntry(r, base)
        smsItem = item
        val (currency, amount) = getSmsLedgerAmount(item, r)
        val kind = item.kind
        // A payment to someone outside Nexo starts as an expense; picking a destination account turns it into a transfer.
        val transfer = kind.isTransfer && (kind != SmsKind.TRANSFER_OUT || item.toAccountId != null)
        val (from, to) = if (kind == SmsKind.TRANSFER_IN) item.toAccountId to item.accountId else item.accountId to item.toAccountId
        val dt = item.occurredAt.toLocalDateTime()
        // The fee field starts filled when the route's preset knows the fee.
        val presetFee = when (val plan = getSmsFeePlan(item)) {
            is FeePlan.Auto -> plan.amount
            is FeePlan.Ask -> plan.prefill.takeIf { !plan.fxDifference }
            FeePlan.None -> null
        }
        val estimated = item.certainty == Certainty.ESTIMATED
        return base.copy(
            mode = EntryMode.SMS,
            fxNote = if (estimated) FxNote(item, editHint = false) else null,
            fxFromApi = estimated && item.rateFromApi,
            feeExpanded = presetFee != null,
            feeAmount = presetFee,
            feeLoaded = true,
            type = when {
                transfer -> EntryType.TRANSFER
                kind.isInflow -> EntryType.INCOME
                else -> EntryType.EXPENSE
            },
            date = dt.toLocalDate(),
            time = dt.toLocalTime(),
            accountId = from,
            toAccountId = if (transfer) to else null,
            categoryId = if (transfer) null else item.categoryId,
            amount = amount,
            currencyId = currency.id,
            feeCurrencyId = r.accountCurrency(from).id,
            note = noteFor(item),
            description = item.note,
            sourceText = item.text,
        )
    }

    /** An SMS transfer's fee goes to the system Bank fee category. */
    private suspend fun withBankFeeCategory(d: TxDraft): TxDraft {
        val fee = d.fee ?: return d
        if (key.mode != EntryMode.SMS) return d
        return d.copy(fee = fee.copy(categoryId = transactions.bankFeeCategory()))
    }

    /** An approved SMS transaction still booked at an FX estimate: editing its amount records the actual one. */
    private var linkedEstimate: SmsItem? = null

    private suspend fun withFxNote(s: EntryForm, txId: String, r: RefData): EntryForm {
        val item = smsRepository.getItemForTransaction(txId)?.takeIf { it.awaitingActual } ?: return s
        linkedEstimate = item
        if (item.estimatedAmount == null) return s
        return s.copy(fxNote = FxNote(item, editHint = true), fxFromApi = item.rateFromApi)
    }

    /** Deny from the form. Nothing is written to the ledger. */
    fun denySms() = viewModelScope.launch {
        key.smsItemId?.let { smsRepository.deny(listOf(it)) }
        close(null)
    }

    /** Pre-fill from a spreadsheet import row. */
    private suspend fun loadImportRow(r: RefData, base: EntryForm): EntryForm {
        val file = key.importFile ?: return newEntry(r, base)
        val order = key.importOrder?.let { runCatching { DateOrder.valueOf(it) }.getOrNull() }
        val row = importer.load(file, order).rows.getOrNull(key.importLine)?.parsed ?: return newEntry(r, base).copy(mode = EntryMode.IMPORT_FIX)
        val kind = row.kind ?: TxKind.EXPENSE
        val transferIn = kind == TxKind.TRANSFER_IN
        val dt = row.dateTime ?: LocalDateTime.now()
        val cur = r.currency(row.currencyId)
        return base.copy(
            mode = EntryMode.IMPORT_FIX,
            type = when (kind) {
                TxKind.INCOME -> EntryType.INCOME
                TxKind.EXPENSE -> EntryType.EXPENSE
                else -> EntryType.TRANSFER
            },
            date = dt.toLocalDate(),
            time = dt.toLocalTime(),
            accountId = if (transferIn) row.counterpartId else row.accountId,
            toAccountId = if (kind.isTransfer) (if (transferIn) row.accountId else row.counterpartId) else null,
            categoryId = row.categoryId,
            amount = row.amount,
            currencyId = cur.id,
            rate = if (cur.id != r.mainCurrency.id && row.amount != 0.0) Money.divide(row.main, row.amount, 10) else null,
            note = row.note,
            description = row.description,
        )
    }

    // ---- Field changes -------------------------------------------------------------------------------------------------

    private fun edit(transform: (EntryForm) -> EntryForm) {
        form.update { s -> transform(if (s.viewing) s.copy(viewing = false) else s) }
    }

    fun enterEdit() = form.update { it.copy(viewing = false) }

    fun revert() {
        originalState?.let { form.value = it.copy(viewing = true) }
    }

    fun setType(t: EntryType) {
        edit { s ->
            if (s.type == t && !s.isBalance) return@edit s
            s.copy(
                type = t,
                balanceKind = null,
                isBalanceAdjustment = false,
                categoryId = null,
                toAccountId = null,
                installments = 0,
                feeExpanded = if (t == EntryType.TRANSFER || (s.mode == EntryMode.SMS && t == EntryType.EXPENSE)) s.feeExpanded else false,
            )
        }
        advance()
    }

    fun openPanel(p: Panel) {
        if (p == Panel.CATEGORY && (form.value.isBalance)) return
        edit { s ->
            val pad = when (p) {
                Panel.AMOUNT -> PadInput("", refCurrencyDecimals(s.currencyId), allowSign = true)
                Panel.FEE -> PadInput("", refCurrencyDecimals(s.feeCurrencyId), allowSign = false)
                else -> s.pad
            }
            s.copy(panel = p, pad = pad)
        }
    }

    fun closePanel() = form.update { it.copy(panel = Panel.NONE) }

    private fun refCurrencyDecimals(id: String) = ref.value.currency(id).decimals

    fun selectAccount(id: String) {
        val r = ref.value
        edit { s ->
            when (s.panel) {
                Panel.TO_ACCOUNT -> s.copy(toAccountId = id)
                else -> {
                    var n = s.copy(accountId = id)
                    if (s.amount == null) n = n.copy(currencyId = r.accountCurrency(id).id, feeCurrencyId = r.accountCurrency(id).id, rate = null)
                    n
                }
            }
        }
        viewModelScope.launch {
            if (form.value.isTransfer && form.value.repeat == RepeatType.NONE && !form.value.feeLoaded) form.value = loadLastFee(form.value)
            advance()
        }
    }

    fun selectCategory(id: String) {
        edit { it.copy(categoryId = id) }
        advance()
    }

    fun swap() {
        val s = form.value
        if (s.accountId == null || s.toAccountId == null) {
            openPanel(if (s.accountId == null) Panel.ACCOUNT else Panel.TO_ACCOUNT)
            return
        }
        edit {
            var n = it.copy(accountId = it.toAccountId, toAccountId = it.accountId, feeLoaded = false)
            if (it.amount == null) n = n.copy(currencyId = ref.value.accountCurrency(n.accountId).id)
            n
        }
        viewModelScope.launch { if (form.value.repeat == RepeatType.NONE) form.value = loadLastFee(form.value) }
    }

    /** Pre-fill the fee of the most recent transfer between the same accounts. */
    private suspend fun loadLastFee(s: EntryForm): EntryForm {
        val from = s.accountId ?: return s
        val to = s.toAccountId ?: return s
        val last = transactions.getLastTransferWithFee(from, to) ?: return s.copy(feeLoaded = true)
        val fee = last.feeGroupId?.let { transactions.getFeeRow(it) } ?: return s.copy(feeLoaded = true)
        return s.copy(feeExpanded = true, feeAmount = fee.amount, feeCurrencyId = fee.currencyId, feeLoaded = true)
    }

    fun padKey(k: PadKey) {
        val s = form.value
        val r = s.pad.press(k)
        val forFee = s.panel == Panel.FEE
        val current = if (forFee) s.feeAmount else s.amount
        val value = when {
            r.negateExisting -> current?.let { -it }
            r.input.value != null -> r.input.value
            r.clearField -> null
            r.input.buffer.isEmpty() && s.pad.buffer.isEmpty() -> current
            else -> null
        }
        form.value = (if (forFee) s.copy(feeAmount = value?.let { abs(it) }) else s.copy(amount = value)).copy(pad = r.input, viewing = false)
    }

    fun padDone() {
        val r = ref.value
        form.update { s ->
            if (s.panel == Panel.FEE) s.copy(feeAmount = s.feeAmount?.let { Money.round(it, r.currency(s.feeCurrencyId).decimals) })
            else s.copy(amount = s.amount?.let { Money.round(it, r.currency(s.currencyId).decimals) })
        }
        advance(fromPad = true)
    }

    fun setPadCurrency(id: String) {
        form.update { s ->
            if (s.panel == Panel.FEE) s.copy(feeCurrencyId = id, pad = PadInput("", refCurrencyDecimals(id), false))
            else s.copy(currencyId = id, rate = null, pad = PadInput("", refCurrencyDecimals(id), true))
        }
    }

    fun calculatorResult(v: BigDecimal) {
        form.update { s ->
            val d = refCurrencyDecimals(if (s.panel == Panel.FEE) s.feeCurrencyId else s.currencyId)
            val value = v.setScale(d, RoundingMode.HALF_UP).toDouble()
            if (s.panel == Panel.FEE) s.copy(feeAmount = abs(value), pad = s.pad.copy(buffer = PadInput.plain(abs(value), d)))
            else s.copy(amount = value, pad = s.pad.copy(buffer = PadInput.plain(value, d)), viewing = false)
        }
    }

    /** Exchange-rate sheet: either the main amount or the rate is edited. */
    fun setMainAmount(main: Double) {
        val s = form.value
        val amount = s.amount ?: return
        if (amount == 0.0) return
        val rate = abs(Money.divide(main, amount, 10))
        edit { it.copy(rate = rate) }
    }

    fun setRate(rate: Double) = edit { it.copy(rate = rate) }

    fun setDate(d: LocalDate) {
        edit { s -> s.copy(date = if (s.repeat != RepeatType.NONE) RepeatSchedule.snap(d, s.repeat) else d) }
        advance()
    }

    fun setTime(t: LocalTime) {
        edit { it.copy(time = t) }
        advance()
    }

    fun setNote(text: String) = edit { it.copy(note = text) }
    fun setDescription(text: String) = edit { it.copy(description = text) }
    fun setTags(ids: List<String>) = edit { it.copy(tagIds = ids) }
    fun removeTag(id: String) = edit { it.copy(tagIds = it.tagIds - id) }
    fun removePhoto(index: Int) = edit { s -> s.copy(photos = s.photos.filterIndexed { i, _ -> i != index }) }

    // ---- Receipt scan --------------------------------------------------------------------------------------------------

    private var scanJob: Job? = null

    /** Reads [uri] on the device; the confirm sheet shows while it runs and then shows what was read. */
    fun scanReceipt(uri: Uri) {
        scanJob?.cancel()
        form.update { it.copy(scan = ScanUi.Reading(uri), panel = Panel.NONE) }
        scanJob = viewModelScope.launch {
            val read = try {
                receipts.scan(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The stack trace carries no receipt text, and it is what a release-only failure needs.
                Log.w("ReceiptScan", "Receipt read failed", e)
                null
            }
            if (read == null) {
                form.update { it.copy(scan = null) }
                showMessage(R.string.receipt_unreadable)
            } else {
                form.update { s -> if (s.scan?.uri == uri) s.copy(scan = ScanUi.Done(uri, read)) else s }
            }
        }
    }

    fun dismissScan() {
        scanJob?.cancel()
        form.update { it.copy(scan = null) }
    }

    /**
     * The user's confirmed reading: [amount] (as shown or corrected, rounded to the entry's currency), the receipt's date
     * and time when [useDate], its shop as the note when [useMerchant] and the note is still empty, and the photo itself.
     * Nothing is saved until the entry is.
     */
    fun applyReceipt(amount: Double?, useDate: Boolean, useMerchant: Boolean) {
        val done = form.value.scan as? ScanUi.Done ?: return
        val read = done.read
        edit { s ->
            val photo = PhotoItem.Picked(done.uri.toString())
            var n = s.copy(scan = null, photos = if (photo in s.photos) s.photos else (s.photos + photo).take(3))
            if (amount != null && amount > 0) {
                val decimals = refCurrencyDecimals(s.currencyId)
                n = n.copy(amount = BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP).toDouble(), pad = PadInput("", decimals, allowSign = true))
            }
            if (useDate && read.date != null) n = n.copy(date = read.date, time = read.time ?: n.time)
            if (useMerchant && read.merchant != null && s.note.isBlank()) n = n.copy(note = read.merchant)
            n
        }
        advance()
    }

    fun setFeeExpanded(expanded: Boolean) {
        edit { s -> if (expanded) s.copy(feeExpanded = true, feeCurrencyId = s.currencyId) else s.copy(feeExpanded = false, feeAmount = null) }
        if (expanded) openPanel(Panel.FEE)
    }

    fun setRepeat(type: RepeatType) {
        edit { s ->
            if (type == RepeatType.NONE) s.copy(repeat = RepeatType.NONE, repeatEnd = null)
            else s.copy(
                repeat = type,
                installments = 0,
                date = RepeatSchedule.snap(s.date, type),
                tagIds = emptyList(),
                feeExpanded = false,
                feeAmount = null,
            )
        }
    }

    fun setRepeatEnd(d: LocalDate?) = edit { it.copy(repeatEnd = d) }

    fun setInstallments(n: Int) = edit { it.copy(installments = n.coerceIn(0, 600), repeat = if (n > 0) RepeatType.NONE else it.repeat) }

    fun clearRepInst() = edit { it.copy(repeat = RepeatType.NONE, installments = 0, repeatEnd = null) }

    fun dismissRepeatConfirm() = form.update { it.copy(confirmRepeat = false) }
    fun setAskDelete(v: Boolean) = form.update { it.copy(askDelete = v) }

    /** Opens the first empty field in visual order. */
    private fun advance(fromPad: Boolean = false) {
        val s = form.value
        if (s.viewing) return
        val next = s.fields.firstOrNull { s.isEmpty(it) && !(it == Field.CATEGORY && s.isBalance) }
        if (next != null) {
            if (fromPad && (next == Field.AMOUNT || next == Field.FEE)) {
                form.update { it.copy(panel = Panel.NONE) }
                return
            }
            openPanel(EntryForm.panelFor(next))
        } else {
            val focus = !s.filledOnce && s.note.isEmpty()
            form.update { it.copy(panel = Panel.NONE, filledOnce = true, noteFocusRequest = if (focus) it.noteFocusRequest + 1 else it.noteFocusRequest) }
        }
    }

    // ---- Save ----------------------------------------------------------------------------------------------------------

    private fun validate(s: EntryForm): Boolean {
        val r = ref.value
        fun fail(msg: Int, panel: Panel): Boolean {
            showMessage(msg)
            openPanel(panel)
            return false
        }
        val accountFirst = s.inputOrder == InputOrder.ACCOUNT_FIRST
        val checks = mutableListOf<() -> Boolean>()
        val accountCheck = { (s.accountId != null && r.accountById.containsKey(s.accountId)) || fail(R.string.select_specific_asset, Panel.ACCOUNT) }
        val secondCheck = {
            if (s.isTransfer) s.toAccountId != null || fail(R.string.select_specific_asset, Panel.TO_ACCOUNT)
            else s.isBalance || s.isBalanceAdjustment || (s.categoryId != null && r.categoryById.containsKey(s.categoryId)) || fail(R.string.select_specific_category, Panel.CATEGORY)
        }
        if (accountFirst || s.isTransfer) { checks += accountCheck; checks += secondCheck } else { checks += secondCheck; checks += accountCheck }
        if (!checks.all { it() }) return false
        if (s.isTransfer && s.accountId == s.toAccountId) {
            showMessage(R.string.same_account_transfer)
            return false
        }
        if (s.repeat != RepeatType.NONE && s.date.isBefore(RepeatSchedule.oldestAllowed(s.repeat, LocalDate.now()))) {
            showMessage(R.string.repeat_too_old)
            return false
        }
        return true
    }

    fun save(continueAfter: Boolean = false) {
        val s = form.value
        if (s.saving || !validate(s)) return
        if (s.repeat != RepeatType.NONE && !s.repeatMode) {
            form.update { it.copy(confirmRepeat = true, panel = Panel.NONE) }
            return
        }
        if (s.repeatMode) {
            saveRule()
            return
        }
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { transactions.save(withBankFeeCategory(draft(form.value))) }
                .onSuccess { id ->
                    home.saved(id)
                    if (key.mode == EntryMode.SMS && key.smsItemId != null) {
                        val s = form.value
                        // Changing an estimate's amount records what the bank actually charged.
                        smsItem?.let { item ->
                            val estimate = item.estimatedAmount
                            if (item.certainty == Certainty.ESTIMATED && s.amount != null && estimate != null && s.amount != estimate) smsRepository.setAmount(item.id, s.amount)
                        }
                        val bankAccount = if (smsItem?.kind == SmsKind.TRANSFER_IN) s.toAccountId else s.accountId
                        val fee = s.feeAmount?.takeIf { s.feeExpanded && it > 0 }
                        if (fee != null && !s.isTransfer) transactions.addFee(id, fee)
                        smsRepository.markApproved(key.smsItemId, id, bankAccount, if (s.isTransfer) null else s.categoryId)
                        smsItem?.let { item -> if (fee != null) smsRepository.recordFee(item, bankAccount, FeeChoice(fee)) }
                    }
                    linkedEstimate?.let { item ->
                        val amount = form.value.amount
                        if (key.mode == EntryMode.EDIT && amount != null && amount != original?.amount) smsRepository.setAmount(item.id, amount)
                    }
                    afterSave(continueAfter)
                }
                .onFailure {
                    form.update { st -> st.copy(saving = false) }
                    showMessage(R.string.save_failed)
                }
        }
    }

    fun confirmRepeatSave() {
        form.update { it.copy(confirmRepeat = false) }
        saveRule()
    }

    private fun saveRule() {
        val s = form.value
        if (s.repeatMode && s.repeat == RepeatType.NONE) {
            close(null)
            return
        }
        val r = ref.value
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cur = r.currency(s.currencyId)
            val rule = RepeatRule(
                id = originalRule?.id ?: UUID.randomUUID().toString(),
                kind = when (s.type) {
                    EntryType.INCOME -> TxKind.INCOME
                    EntryType.EXPENSE -> TxKind.EXPENSE
                    EntryType.TRANSFER -> TxKind.TRANSFER_OUT
                },
                accountId = s.accountId,
                toAccountId = if (s.isTransfer) s.toAccountId else null,
                categoryId = if (s.isTransfer) null else s.categoryId,
                currencyId = cur.id,
                amount = Money.round(s.amount ?: 0.0, cur.decimals),
                note = s.note.trim(),
                description = s.description,
                repeatType = s.repeat,
                nextDate = s.date.toIsoText(),
                endDate = s.repeatEnd?.toIsoText(),
                anchorDay = s.date.dayOfMonth,
                createdAt = originalRule?.createdAt ?: now,
                updatedAt = now,
            )
            repeats.save(rule)
            close(s.date)
        }
    }

    private fun draft(s: EntryForm) = TxDraft(
        editingId = s.originalId,
        type = s.type,
        balanceKind = s.balanceKind,
        isBalanceAdjustment = s.isBalanceAdjustment && (s.isBalance || s.categoryId == null),
        accountId = s.accountId,
        toAccountId = if (s.isTransfer) s.toAccountId else null,
        categoryId = if (s.isTransfer) null else s.categoryId,
        dateTime = s.date.atTime(if (s.showTime) s.time else LocalTime.MIDNIGHT),
        amount = s.amount ?: 0.0,
        currencyId = s.currencyId,
        rate = s.rate,
        note = s.note,
        description = s.description,
        tagIds = s.tagIds,
        photos = s.photos,
        fee = if (s.isTransfer && s.feeExpanded && s.feeAmount != null) FeeDraft(s.feeAmount, s.feeCurrencyId) else null,
        installments = if (s.type == EntryType.EXPENSE && !s.isBalance) s.installments else 0,
        sourceText = s.sourceText,
    )

    private suspend fun afterSave(continueAfter: Boolean) {
        val s = form.value
        if (key.reviewUnassigned) {
            // Bulk-assign other unassigned entries with the same note.
            val orig = original
            if (orig != null && orig.categoryId == null && s.categoryId != null && s.note.isNotBlank()) {
                val same = transactions.getUnassigned("9999-12-31").filter { it.id != orig.id && it.note == s.note && it.categoryId == null && !it.kind.isTransfer }
                if (same.isNotEmpty()) {
                    transactions.setCategory(same.map { it.id }, s.categoryId)
                    showMessage(R.string.bulk_assigned, same.size)
                }
            }
            openNextUnassigned()
            return
        }
        if (key.mode == EntryMode.IMPORT_FIX && key.importFile != null) {
            importer.removeLine(key.importFile, key.importLine)
        }
        if (continueAfter) {
            val r = refs.getRefData()
            val fresh = newEntry(r, EntryForm(inputOrder = r.settings.inputOrder, currencyId = r.mainCurrency.id, feeCurrencyId = r.mainCurrency.id))
                .copy(type = s.type, date = s.date, time = s.time, loaded = true)
            form.value = fresh
            advance()
            showMessage(R.string.saved)
        } else {
            close(s.date)
        }
    }

    private suspend fun openNextUnassigned() {
        val s = form.value
        val next = transactions.getUnassigned(s.date.toIsoText()).firstOrNull { it.id != s.originalId }
        if (next == null) close(s.date)
        else open(EntryKey(mode = EntryMode.EDIT, txId = next.id, reviewUnassigned = true))
    }

    fun skip() = viewModelScope.launch { openNextUnassigned() }

    fun delete(wholeGroup: Boolean) {
        val id = form.value.originalId ?: return
        viewModelScope.launch {
            transactions.delete(listOf(id), wholeGroup)
            if (key.reviewUnassigned) openNextUnassigned() else close(form.value.date)
        }
    }

    fun bookmark() {
        val tx = original ?: return
        viewModelScope.launch {
            favorites.addFromTransaction(tx)
            showMessage(R.string.bookmark_registered)
            close(null)
        }
    }

    fun copy(withToday: Boolean) {
        val id = form.value.originalId ?: return
        viewModelScope.launch {
            prefs.setCopyWithToday(withToday)
            open(EntryKey(mode = EntryMode.COPY, txId = id, copyWithToday = withToday))
        }
    }


    fun markTransferGuideShown() = viewModelScope.launch { prefs.setTransferGuideShown() }
    fun markPhotoNoticeShown() = viewModelScope.launch { prefs.setPhotoNoticeShown() }
    fun setGroupedPicker(v: Boolean) = viewModelScope.launch { prefs.setGroupedPicker(v) }
}
