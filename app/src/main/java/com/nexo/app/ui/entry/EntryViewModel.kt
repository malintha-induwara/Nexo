package com.nexo.app.ui.entry

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.toIsoText
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.FavoriteRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.data.RepeatRepository
import com.nexo.app.data.TransactionWriter
import com.nexo.app.domain.CardStatement
import com.nexo.app.domain.Conversion
import com.nexo.app.domain.FeeDraft
import com.nexo.app.domain.PadInput
import com.nexo.app.domain.PadKey
import com.nexo.app.domain.PhotoItem
import com.nexo.app.domain.RefData
import com.nexo.app.domain.RepeatSchedule
import com.nexo.app.domain.TxDraft
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.sms.rateFromApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@HiltViewModel(assistedFactory = EntryViewModel.Factory::class)
class EntryViewModel @AssistedInject constructor(
    @Assisted val key: EntryKey,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val writer: TransactionWriter,
    private val favorites: FavoriteRepository,
    private val repeats: RepeatRepository,
    private val prefs: DevicePrefs,
    private val home: HomeState,
    val photoStore: com.nexo.app.core.files.PhotoStore,
    val tagRepo: com.nexo.app.data.TagRepository,
    private val importer: com.nexo.app.backup.TsvImporter,
    private val sms: com.nexo.app.sms.approve.SmsApprover,
    private val receipts: com.nexo.app.receipt.ReceiptScanner,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: EntryKey): EntryViewModel
    }

    private val _ui = MutableStateFlow(EntryUi())
    val ui: StateFlow<EntryUi> = _ui.asStateFlow()

    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = Channel<EntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _suggestions = MutableStateFlow<List<NoteSuggestion>>(emptyList())
    val suggestions: StateFlow<List<NoteSuggestion>> = _suggestions.asStateFlow()

    private var originalState: EntryUi? = null
    private var original: TransactionEntity? = null
    private var originalRule: RepeatRuleEntity? = null

    init {
        viewModelScope.launch {
            val r = refs.snapshot()
            val base = EntryUi(inputOrder = r.settings.inputOrder, currencyId = r.mainCurrency.id, feeCurrencyId = r.mainCurrency.id)
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
            val unassigned = if (key.reviewUnassigned) db.transactionDao().unassigned(loaded.date.toIsoText()).size else 0
            _ui.value = loaded.copy(loaded = true, unassignedCount = unassigned)
            originalState = _ui.value
            if (!loaded.viewing && key.mode != EntryMode.PAY_CARD) advance()
            loadSuggestions(r)
        }
        viewModelScope.launch {
            home.pickedFavorite.collect { id -> refs.snapshot().let { r -> _ui.value = applyFavorite(r, _ui.value, id, keepDate = true) } }
        }
    }

    private suspend fun loadSuggestions(r: RefData) {
        if (!r.settings.autocomplete) return
        val cutoff = maxOf(System.currentTimeMillis() - 456L * 24 * 3600 * 1000, r.settings.autocompleteResetAt)
        val cutDate = java.time.Instant.ofEpochMilli(cutoff).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toIsoText()
        _suggestions.value = db.transactionDao().noteUsage(cutDate, cutoff).map { NoteSuggestion(it.note, it.count, it.lastDate) }
    }

    private fun newEntry(r: RefData, base: EntryUi): EntryUi {
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

    private suspend fun loadExisting(r: RefData, base: EntryUi, copy: Boolean): EntryUi {
        var tx = key.txId?.let { db.transactionDao().getById(it) } ?: return newEntry(r, base)
        if (tx.kind == TxKind.TRANSFER_IN) {
            val out = tx.transferGroupId?.let { g -> db.transactionDao().getByTransferGroup(g).firstOrNull { it.kind == TxKind.TRANSFER_OUT } }
            tx = out ?: tx.copy(kind = TxKind.TRANSFER_OUT, accountId = tx.counterpartAccountId, counterpartAccountId = tx.accountId)
        }
        original = if (copy) null else tx
        val dateTime = writer.localDateTime(tx.dateTime)
        val storedDate = runCatching { LocalDate.parse(tx.date) }.getOrDefault(dateTime.toLocalDate())
        val type = when (tx.kind) {
            TxKind.INCOME, TxKind.BALANCE_INCREASE -> EntryType.INCOME
            TxKind.EXPENSE, TxKind.BALANCE_DECREASE -> EntryType.EXPENSE
            else -> EntryType.TRANSFER
        }
        val fee = if (tx.kind.isTransfer) tx.feeGroupId?.let { db.transactionDao().getFeeRow(it) } else null
        val tags = db.tagDao().linksForTransaction(tx.id).sortedBy { it.position }.map { it.tagId }
        val photos = if (copy) emptyList() else db.photoDao().forTransaction(tx.id).map { PhotoItem.Stored(it.fileName, it.checksum, it.fileSize) }
        val rate = if (tx.currencyId != r.mainCurrency.id && tx.amount != 0.0) {
            BigDecimal.valueOf(tx.amountMain).divide(BigDecimal.valueOf(tx.amount), 10, RoundingMode.HALF_UP).toDouble()
        } else null
        val installment = if (!copy && tx.installmentGroupId != null) {
            val group = db.transactionDao().getByInstallmentGroup(tx.installmentGroupId).filter { it.kind != TxKind.TRANSFER_IN }
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

    private suspend fun applyFavorite(r: RefData, base: EntryUi, favoriteId: String?, keepDate: Boolean = false): EntryUi {
        val fav = favoriteId?.let { db.favoriteDao().getById(it) } ?: return base
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

    private suspend fun loadRule(r: RefData, base: EntryUi): EntryUi {
        val rule = key.repeatRuleId?.let { db.repeatRuleDao().getById(it) } ?: return newEntry(r, base)
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

    private suspend fun loadPayCard(r: RefData, base: EntryUi): EntryUi {
        val card = key.accountId?.let { r.accountById[it] } ?: return newEntry(r, base)
        val ids = listOf(card.id) + r.linkedDebitCards(card.id).map { it.id }
        val figures = CardStatement.compute(r, card, db.transactionDao().getAllForAccounts(ids), LocalDate.now())
        val payer = card.linkedAccountId?.takeIf { id -> r.accountById[id]?.let { r.isAccountVisible(it) } == true }
        return base.copy(
            mode = EntryMode.PAY_CARD,
            type = EntryType.TRANSFER,
            accountId = payer,
            toAccountId = card.id,
            currencyId = card.currencyId,
            feeCurrencyId = card.currencyId,
            amount = figures.payAmount,
            note = context.getString(R.string.payment),
        )
    }

    /** The Review Queue item being reviewed in [EntryMode.SMS] (spec-money-master-sl 04 NQ-003 Edit). */
    private var smsItem: com.nexo.app.sms.data.SmsItemEntity? = null

    /** NQ-003: the form opens with everything the SMS gave; saving approves the item. */
    private suspend fun loadSmsItem(r: RefData, base: EntryUi): EntryUi {
        val item = key.smsItemId?.let { db.smsDao().item(it) } ?: return newEntry(r, base)
        smsItem = item
        val (currency, amount) = sms.amountIn(item, r)
        val kind = item.kind
        // A payment to someone outside Nexo starts as an expense; picking a destination account turns it into a transfer.
        val transfer = kind.isTransfer && (kind != com.nexo.app.sms.SmsKind.TRANSFER_OUT || item.toAccountId != null)
        val (from, to) = if (kind == com.nexo.app.sms.SmsKind.TRANSFER_IN) item.toAccountId to item.accountId else item.accountId to item.toAccountId
        val dt = com.nexo.app.sms.approve.SmsApprover.localDateTime(item.occurredAt)
        // NQ-020 in the form: the fee field starts filled when the route's preset knows the fee.
        val presetFee = when (val plan = sms.feePlan(item)) {
            is com.nexo.app.sms.approve.FeePlan.Auto -> plan.amount
            is com.nexo.app.sms.approve.FeePlan.Ask -> plan.prefill.takeIf { !plan.fxDifference }
            com.nexo.app.sms.approve.FeePlan.None -> null
        }
        val estimated = item.certainty == com.nexo.app.sms.Certainty.ESTIMATED
        return base.copy(
            mode = EntryMode.SMS,
            fxNote = if (estimated) com.nexo.app.ui.sms.fxEstimateNote(context, item, r) else null,
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
            note = com.nexo.app.sms.approve.SmsApprover.noteFor(item),
            description = item.note,
            sourceText = item.text,
        )
    }

    /** An SMS transfer's fee goes to the system Bank fee category (spec-money-master-sl 02 improvement 1). */
    private suspend fun withBankFeeCategory(d: com.nexo.app.domain.TxDraft): com.nexo.app.domain.TxDraft {
        val fee = d.fee ?: return d
        if (key.mode != EntryMode.SMS) return d
        return d.copy(fee = fee.copy(categoryId = writer.bankFeeCategory()))
    }

    /** An approved SMS transaction still booked at an FX estimate (NQ-012): editing its amount records the actual one. */
    private var linkedEstimate: com.nexo.app.sms.data.SmsItemEntity? = null

    private suspend fun withFxNote(s: EntryUi, txId: String, r: RefData): EntryUi {
        val item = db.smsDao().byTransaction(txId)?.takeIf { it.awaitingActual } ?: return s
        linkedEstimate = item
        val note = com.nexo.app.ui.sms.fxEstimateNote(context, item, r) ?: return s
        return s.copy(fxNote = note + "\n" + context.getString(R.string.fx_edit_hint), fxFromApi = item.rateFromApi)
    }

    /** NQ-003 Deny from the form. Nothing is written to the ledger. */
    fun denySms() = viewModelScope.launch {
        key.smsItemId?.let { sms.deny(listOf(it)) }
        _events.send(EntryEvent.Close(null))
    }

    /** TX-036: pre-fill from a spreadsheet import row. */
    private suspend fun loadImportRow(r: RefData, base: EntryUi): EntryUi {
        val file = key.importFile ?: return newEntry(r, base)
        val order = key.importOrder?.let { runCatching { com.nexo.app.backup.DateOrder.valueOf(it) }.getOrNull() }
        val row = importer.load(file, order).rows.getOrNull(key.importLine)?.parsed ?: return newEntry(r, base).copy(mode = EntryMode.IMPORT_FIX)
        val kind = row.kind ?: TxKind.EXPENSE
        val transferIn = kind == TxKind.TRANSFER_IN
        val dt = row.dateTime ?: java.time.LocalDateTime.now()
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

    private fun edit(transform: (EntryUi) -> EntryUi) {
        _ui.update { s -> transform(if (s.viewing) s.copy(viewing = false) else s) }
    }

    fun enterEdit() = _ui.update { it.copy(viewing = false) }

    fun revert() {
        originalState?.let { _ui.value = it.copy(viewing = true) }
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
        if (p == Panel.CATEGORY && (_ui.value.isBalance)) return
        edit { s ->
            val pad = when (p) {
                Panel.AMOUNT -> PadInput("", refCurrencyDecimals(s.currencyId), allowSign = true)
                Panel.FEE -> PadInput("", refCurrencyDecimals(s.feeCurrencyId), allowSign = false)
                else -> s.pad
            }
            s.copy(panel = p, pad = pad)
        }
    }

    fun closePanel() = _ui.update { it.copy(panel = Panel.NONE) }

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
            if (_ui.value.isTransfer && _ui.value.repeat == RepeatType.NONE && !_ui.value.feeLoaded) _ui.value = loadLastFee(_ui.value)
            advance()
        }
    }

    fun selectCategory(id: String) {
        edit { it.copy(categoryId = id) }
        advance()
    }

    fun swap() {
        val s = _ui.value
        if (s.accountId == null || s.toAccountId == null) {
            openPanel(if (s.accountId == null) Panel.ACCOUNT else Panel.TO_ACCOUNT)
            return
        }
        edit {
            var n = it.copy(accountId = it.toAccountId, toAccountId = it.accountId, feeLoaded = false)
            if (it.amount == null) n = n.copy(currencyId = ref.value.accountCurrency(n.accountId).id)
            n
        }
        viewModelScope.launch { if (_ui.value.repeat == RepeatType.NONE) _ui.value = loadLastFee(_ui.value) }
    }

    /** TX-011: pre-fill the fee of the most recent transfer between the same accounts. */
    private suspend fun loadLastFee(s: EntryUi): EntryUi {
        val from = s.accountId ?: return s
        val to = s.toAccountId ?: return s
        val last = db.transactionDao().lastTransferWithFee(from, to) ?: return s.copy(feeLoaded = true)
        val fee = last.feeGroupId?.let { db.transactionDao().getFeeRow(it) } ?: return s.copy(feeLoaded = true)
        return s.copy(feeExpanded = true, feeAmount = fee.amount, feeCurrencyId = fee.currencyId, feeLoaded = true)
    }

    fun padKey(k: PadKey) {
        val s = _ui.value
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
        _ui.value = (if (forFee) s.copy(feeAmount = value?.let { kotlin.math.abs(it) }) else s.copy(amount = value)).copy(pad = r.input, viewing = false)
    }

    fun padDone() {
        val r = ref.value
        _ui.update { s ->
            if (s.panel == Panel.FEE) s.copy(feeAmount = s.feeAmount?.let { Money.round(it, r.currency(s.feeCurrencyId).decimals) })
            else s.copy(amount = s.amount?.let { Money.round(it, r.currency(s.currencyId).decimals) })
        }
        advance(fromPad = true)
    }

    fun setPadCurrency(id: String) {
        _ui.update { s ->
            if (s.panel == Panel.FEE) s.copy(feeCurrencyId = id, pad = PadInput("", refCurrencyDecimals(id), false))
            else s.copy(currencyId = id, rate = null, pad = PadInput("", refCurrencyDecimals(id), true))
        }
    }

    fun calculatorResult(v: BigDecimal) {
        _ui.update { s ->
            val d = refCurrencyDecimals(if (s.panel == Panel.FEE) s.feeCurrencyId else s.currencyId)
            val value = v.setScale(d, RoundingMode.HALF_UP).toDouble()
            if (s.panel == Panel.FEE) s.copy(feeAmount = kotlin.math.abs(value), pad = s.pad.copy(buffer = PadInput.plain(kotlin.math.abs(value), d)))
            else s.copy(amount = value, pad = s.pad.copy(buffer = PadInput.plain(value, d)), viewing = false)
        }
    }

    /** Exchange-rate sheet (TX-010): either the main amount or the rate is edited. */
    fun setMainAmount(main: Double) {
        val s = _ui.value
        val amount = s.amount ?: return
        if (amount == 0.0) return
        val rate = kotlin.math.abs(Money.divide(main, amount, 10))
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
        _ui.update { it.copy(scan = ScanUi.Reading(uri), panel = Panel.NONE) }
        scanJob = viewModelScope.launch {
            val read = try {
                receipts.scan(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The stack trace carries no receipt text, and it is what a release-only failure needs.
                android.util.Log.w("ReceiptScan", "Receipt read failed", e)
                null
            }
            if (read == null) {
                _ui.update { it.copy(scan = null) }
                _events.send(EntryEvent.Message(R.string.receipt_unreadable))
            } else {
                _ui.update { s -> if (s.scan?.uri == uri) s.copy(scan = ScanUi.Done(uri, read)) else s }
            }
        }
    }

    fun dismissScan() {
        scanJob?.cancel()
        _ui.update { it.copy(scan = null) }
    }

    /**
     * The user's confirmed reading: [amount] (as shown or corrected, rounded to the entry's currency), the receipt's date
     * and time when [useDate], its shop as the note when [useMerchant] and the note is still empty, and the photo itself.
     * Nothing is saved until the entry is.
     */
    fun applyReceipt(amount: Double?, useDate: Boolean, useMerchant: Boolean) {
        val done = _ui.value.scan as? ScanUi.Done ?: return
        val read = done.read
        edit { s ->
            val photo = PhotoItem.Picked(done.uri)
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

    fun dismissRepeatConfirm() = _ui.update { it.copy(confirmRepeat = false) }
    fun setAskDelete(v: Boolean) = _ui.update { it.copy(askDelete = v) }

    /** Opens the first empty field in visual order (TX-024). */
    private fun advance(fromPad: Boolean = false) {
        val s = _ui.value
        if (s.viewing) return
        val next = s.fields.firstOrNull { s.isEmpty(it) && !(it == Field.CATEGORY && s.isBalance) }
        if (next != null) {
            if (fromPad && (next == Field.AMOUNT || next == Field.FEE)) {
                _ui.update { it.copy(panel = Panel.NONE) }
                return
            }
            openPanel(EntryUi.panelFor(next))
        } else {
            val focus = !s.filledOnce && s.note.isEmpty()
            _ui.update { it.copy(panel = Panel.NONE, filledOnce = true, noteFocusRequest = if (focus) it.noteFocusRequest + 1 else it.noteFocusRequest) }
        }
    }

    // ---- Save ----------------------------------------------------------------------------------------------------------

    private fun validate(s: EntryUi): Boolean {
        val r = ref.value
        fun fail(msg: Int, panel: Panel): Boolean {
            _events.trySend(EntryEvent.Message(msg))
            openPanel(panel)
            return false
        }
        val accountFirst = s.inputOrder == com.nexo.app.core.model.InputOrder.ACCOUNT_FIRST
        val checks = mutableListOf<() -> Boolean>()
        val accountCheck = { s.accountId != null && r.accountById.containsKey(s.accountId) || fail(R.string.select_specific_asset, Panel.ACCOUNT) }
        val secondCheck = {
            if (s.isTransfer) s.toAccountId != null || fail(R.string.select_specific_asset, Panel.TO_ACCOUNT)
            else s.isBalance || s.isBalanceAdjustment || (s.categoryId != null && r.categoryById.containsKey(s.categoryId)) || fail(R.string.select_specific_category, Panel.CATEGORY)
        }
        if (accountFirst || s.isTransfer) { checks += accountCheck; checks += secondCheck } else { checks += secondCheck; checks += accountCheck }
        if (!checks.all { it() }) return false
        if (s.isTransfer && s.accountId == s.toAccountId) {
            _events.trySend(EntryEvent.Message(R.string.same_account_transfer))
            return false
        }
        if (s.repeat != RepeatType.NONE && s.date.isBefore(RepeatSchedule.oldestAllowed(s.repeat, LocalDate.now()))) {
            _events.trySend(EntryEvent.Message(R.string.repeat_too_old))
            return false
        }
        return true
    }

    fun save(continueAfter: Boolean = false) {
        val s = _ui.value
        if (s.saving || !validate(s)) return
        if (s.repeat != RepeatType.NONE && !s.repeatMode) {
            _ui.update { it.copy(confirmRepeat = true, panel = Panel.NONE) }
            return
        }
        if (s.repeatMode) {
            saveRule()
            return
        }
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { writer.save(withBankFeeCategory(draft(_ui.value))) }
                .onSuccess { id ->
                    home.saved(id)
                    if (key.mode == EntryMode.SMS && key.smsItemId != null) {
                        val s = _ui.value
                        // NQ-012: changing an estimate's amount records what the bank actually charged.
                        smsItem?.let { item ->
                            val estimate = item.estimatedAmount
                            if (item.certainty == com.nexo.app.sms.Certainty.ESTIMATED && s.amount != null && estimate != null && s.amount != estimate) sms.setAmount(item.id, s.amount)
                        }
                        val bankAccount = if (smsItem?.kind == com.nexo.app.sms.SmsKind.TRANSFER_IN) s.toAccountId else s.accountId
                        val fee = s.feeAmount?.takeIf { s.feeExpanded && it > 0 }
                        if (fee != null && !s.isTransfer) writer.addFee(id, fee)
                        sms.markApproved(key.smsItemId, id, bankAccount, if (s.isTransfer) null else s.categoryId)
                        smsItem?.let { item -> if (fee != null) sms.recordFee(item, bankAccount, com.nexo.app.sms.approve.FeeChoice(fee)) }
                    }
                    linkedEstimate?.let { item ->
                        val amount = _ui.value.amount
                        if (key.mode == EntryMode.EDIT && amount != null && amount != original?.amount) sms.setAmount(item.id, amount)
                    }
                    afterSave(continueAfter)
                }
                .onFailure {
                    _ui.update { st -> st.copy(saving = false) }
                    _events.trySend(EntryEvent.Message(R.string.save_failed))
                }
        }
    }

    fun confirmRepeatSave() {
        _ui.update { it.copy(confirmRepeat = false) }
        saveRule()
    }

    private fun saveRule() {
        val s = _ui.value
        if (s.repeatMode && s.repeat == RepeatType.NONE) {
            _events.trySend(EntryEvent.Close(null))
            return
        }
        val r = ref.value
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cur = r.currency(s.currencyId)
            val rule = RepeatRuleEntity(
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
            _events.send(EntryEvent.Close(s.date))
        }
    }

    private fun draft(s: EntryUi) = TxDraft(
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
        val s = _ui.value
        if (key.reviewUnassigned) {
            // Bulk-assign other unassigned entries with the same note (TX-025).
            val orig = original
            if (orig != null && orig.categoryId == null && s.categoryId != null && s.note.isNotBlank()) {
                val same = db.transactionDao().unassigned("9999-12-31").filter { it.id != orig.id && it.note == s.note && it.categoryId == null && !it.kind.isTransfer }
                if (same.isNotEmpty()) {
                    writer.setCategory(same.map { it.id }, s.categoryId)
                    _events.send(EntryEvent.Message(R.string.bulk_assigned, same.size))
                }
            }
            openNextUnassigned()
            return
        }
        if (key.mode == EntryMode.IMPORT_FIX && key.importFile != null) {
            importer.removeLine(key.importFile, key.importLine)
        }
        if (continueAfter) {
            val r = refs.snapshot()
            val fresh = newEntry(r, EntryUi(inputOrder = r.settings.inputOrder, currencyId = r.mainCurrency.id, feeCurrencyId = r.mainCurrency.id))
                .copy(type = s.type, date = s.date, time = s.time, loaded = true)
            _ui.value = fresh
            advance()
            _events.send(EntryEvent.Message(R.string.saved))
        } else {
            _events.send(EntryEvent.Close(s.date))
        }
    }

    private suspend fun openNextUnassigned() {
        val s = _ui.value
        val next = db.transactionDao().unassigned(s.date.toIsoText()).firstOrNull { it.id != s.originalId }
        if (next == null) _events.send(EntryEvent.Close(s.date))
        else _events.send(EntryEvent.Open(EntryKey(mode = EntryMode.EDIT, txId = next.id, reviewUnassigned = true)))
    }

    fun skip() = viewModelScope.launch { openNextUnassigned() }

    fun delete(wholeGroup: Boolean) {
        val id = _ui.value.originalId ?: return
        viewModelScope.launch {
            writer.delete(listOf(id), wholeGroup)
            if (key.reviewUnassigned) openNextUnassigned() else _events.send(EntryEvent.Close(_ui.value.date))
        }
    }

    fun bookmark() {
        val tx = original ?: return
        viewModelScope.launch {
            favorites.addFromTransaction(tx)
            _events.send(EntryEvent.Message(R.string.bookmark_registered))
            _events.send(EntryEvent.Close(null))
        }
    }

    fun copy(withToday: Boolean) {
        val id = _ui.value.originalId ?: return
        viewModelScope.launch {
            prefs.setCopyWithToday(withToday)
            _events.send(EntryEvent.Open(EntryKey(mode = EntryMode.COPY, txId = id, copyWithToday = withToday)))
        }
    }

    fun focus(date: LocalDate) = home.focusOn(date)

    fun markTransferGuideShown() = viewModelScope.launch { prefs.setTransferGuideShown() }
    fun markPhotoNoticeShown() = viewModelScope.launch { prefs.setPhotoNoticeShown() }
    fun setGroupedPicker(v: Boolean) = viewModelScope.launch { prefs.setGroupedPicker(v) }
}
