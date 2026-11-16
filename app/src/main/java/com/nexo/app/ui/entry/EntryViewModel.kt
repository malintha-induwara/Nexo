package com.nexo.app.ui.entry

import android.net.Uri
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.InputOrder
import com.nexo.app.core.model.PhotoItem
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatSchedule
import com.nexo.app.core.model.RepeatType
import com.nexo.app.data.repository.FavoritesRepository
import com.nexo.app.data.repository.PhotosRepository
import com.nexo.app.data.repository.ReceiptRepository
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.RepeatRulesRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TagsRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.domain.entry.EntrySave
import com.nexo.app.domain.entry.EstimateEdit
import com.nexo.app.domain.entry.GetNoteSuggestionsUseCase
import com.nexo.app.domain.entry.ImportLine
import com.nexo.app.domain.entry.SaveEntryResult
import com.nexo.app.domain.entry.SaveEntryUseCase
import com.nexo.app.domain.entry.SmsReview
import com.nexo.app.ui.common.getJson
import com.nexo.app.ui.common.setJson
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.UserMessage
import com.nexo.app.ui.designsystem.component.PadInput
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.NavResult
import com.nexo.app.ui.tags.TagActions
import com.nexo.app.ui.tags.TagEditor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val photos: PhotosRepository,
    private val tags: TagsRepository,
    private val receipts: ReceiptRepository,
    private val loader: EntryFormLoader,
    private val saveEntry: SaveEntryUseCase,
    private val getNoteSuggestions: GetNoteSuggestionsUseCase,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(key: EntryKey): EntryViewModel
    }

    private val form =
        MutableStateFlow(EntryForm(date = LocalDate.now(clock), time = loader.timeNow()))

    /**
     * Kept for event handlers that need the reference data synchronously. It feeds [uiState], so it
     * is current whenever the screen is there to call them.
     */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RefData(),
    )

    private val suggestions = MutableStateFlow<List<NoteSuggestion>>(emptyList())

    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val navigation = MutableStateFlow<EntryNavigation?>(null)
    private val results = MutableStateFlow<List<NavResult>>(emptyList())
    private val photoExported = MutableStateFlow<Boolean?>(null)

    private val tagEditor = TagEditor(tags, viewModelScope) {
        uiState.value.tags.filter { !it.isDeleted }
    }

    /** What the tag picker can do. */
    val tagActions: TagActions = tagEditor.actions

    /** Looked up again only when the form's stored photos change. */
    private val photoFiles: Flow<Map<String, File>> = form
        .map { f -> f.photos.filterIsInstance<PhotoItem.Stored>().map { it.fileName } }
        .distinctUntilChanged()
        .map(photos::getPhotoFiles)

    val uiState: StateFlow<EntryUiState> = combine(
        combine(form, ref, prefs.getDeviceSettingsStream(), ::Triple),
        combine(suggestions, tags.getTagsStream(), tagEditor.dialog, ::Triple),
        combine(photoFiles, photoExported, ::Pair),
        combine(userMessage, navigation, results, ::Triple),
    ) { (form, r, device), (suggestions, tagList, tagDialog), (files, exported), effects ->
        val (message, navigation, results) = effects
        EntryUiState(
            form = form,
            ref = r,
            device = device,
            suggestions = suggestions,
            tags = tagList,
            tagDialog = tagDialog,
            photoFiles = files,
            photoExported = exported,
            userMessage = message,
            navigation = navigation,
            results = results,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryUiState(form.value))

    private var initialized = false

    private var originalState: EntryForm? = null

    /** The stored records the form was opened from; see [LoadedEntry]. */
    private var opened = LoadedEntry(form.value)

    /** Loads the form once; safe to call again. [texts] are the strings the form needs as data. */
    @MainThread
    fun initialize(texts: EntryTexts) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val r = refs.getRefData()
            opened = loader.load(key, r, texts)
            val loaded = opened.form
            val unassigned = if (key.reviewUnassigned) {
                transactions.getUnassigned(loaded.date.toIsoText()).size
            } else {
                0
            }
            val start = loaded.copy(loaded = true, unassignedCount = unassigned)
            originalState = start
            val saved = savedState.getJson<SavedEntryForm>(SAVED_FORM)
            if (saved != null) {
                // Back after the process was killed: the user's input over the freshly loaded form.
                val readable = saved.photos.mapNotNull { it.uri }
                    .filter { photos.canRead(Uri.parse(it)) }.toSet()
                form.value = saved.appliedTo(start) { it in readable }
            } else {
                form.value = start
                if (!loaded.viewing && key.mode != EntryMode.PAY_CARD) advance()
            }
            suggestions.value = getNoteSuggestions().map {
                NoteSuggestion(it.note, it.count, it.lastDate)
            }
            form.collect { savedState.setJson(SAVED_FORM, it.toSaved()) }
        }
    }

    /** A bookmark picked from the list this form opened; it fills the form but keeps the date. */
    fun applyFavorite(id: String) = viewModelScope.launch {
        form.first { it.loaded }
        form.value = loader.applyFavorite(refs.getRefData(), form.value, id, keepDate = true)
    }

    fun onUserMessageShown(id: Long) {
        userMessage.update { if (it?.id == id) null else it }
    }

    fun onNavigationHandled() {
        navigation.value = null
    }

    fun onResultsDelivered(delivered: List<NavResult>) {
        results.update { it - delivered.toSet() }
    }

    private fun showMessage(@StringRes text: Int, arg: Int? = null) {
        userMessage.value = UserMessage(UiText.Resource(text, listOfNotNull(arg)))
    }

    /** Leaves the form; with [focusDate] the Transactions tab scrolls there. */
    private fun close(focusDate: LocalDate?) {
        focusDate?.let { results.update { r -> r + NavResult.Focus(it) } }
        navigation.value = EntryNavigation.Close
    }

    private fun open(key: EntryKey, replace: Boolean = true) {
        navigation.value = EntryNavigation.Open(key, replace)
    }

    /** Saves a copy of a stored photo to the phone's gallery. */
    fun exportPhoto(fileName: String) {
        viewModelScope.launch { photoExported.value = photos.exportToGallery(fileName) }
    }

    fun onPhotoExportShown() {
        photoExported.value = null
    }

    /** Deny from the form. Nothing is written to the ledger. */
    fun denySms() = viewModelScope.launch {
        key.smsItemId?.let { smsRepository.deny(listOf(it)) }
        close(null)
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
        edit { it.withType(t) }
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
        edit { it.withAccount(id, ref.value) }
        viewModelScope.launch {
            if (form.value.isTransfer && form.value.repeat == RepeatType.NONE &&
                !form.value.feeLoaded
            ) {
                form.value = loader.withLastFee(form.value)
            }
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
        edit { it.swapped(ref.value) }
        viewModelScope.launch {
            if (form.value.repeat == RepeatType.NONE) {
                form.value = loader.withLastFee(form.value)
            }
        }
    }

    fun padKey(k: PadKey) {
        form.value = form.value.afterPadKey(k)
    }

    fun padDone() {
        val r = ref.value
        form.update { s ->
            if (s.panel == Panel.FEE) {
                s.copy(
                    feeAmount = s.feeAmount?.let {
                        Money.round(it, r.currency(s.feeCurrencyId).decimals)
                    },
                )
            } else {
                s.copy(
                    amount = s.amount?.let {
                        Money.round(it, r.currency(s.currencyId).decimals)
                    },
                )
            }
        }
        advance(fromPad = true)
    }

    fun setPadCurrency(id: String) {
        form.update { s ->
            if (s.panel == Panel.FEE) {
                s.copy(feeCurrencyId = id, pad = PadInput("", refCurrencyDecimals(id), false))
            } else {
                s.copy(
                    currencyId = id,
                    rate = null,
                    pad = PadInput("", refCurrencyDecimals(id), true),
                )
            }
        }
    }

    fun calculatorResult(v: BigDecimal) {
        form.update { s ->
            val d = refCurrencyDecimals(if (s.panel == Panel.FEE) s.feeCurrencyId else s.currencyId)
            val value = v.setScale(d, RoundingMode.HALF_UP).toDouble()
            if (s.panel == Panel.FEE) {
                s.copy(
                    feeAmount = abs(value),
                    pad = s.pad.copy(buffer = PadInput.plain(abs(value), d)),
                )
            } else {
                s.copy(
                    amount = value,
                    pad = s.pad.copy(buffer = PadInput.plain(value, d)),
                    viewing = false,
                )
            }
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
        edit { it.withDate(d) }
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
    fun removePhoto(index: Int) = edit { s ->
        s.copy(
            photos = s.photos.filterIndexed { i, _ ->
                i !=
                    index
            },
        )
    }

    // ---- Receipt scan --------------------------------------------------------------------------------------------------

    private var scanJob: Job? = null

    /** A file picked from the gallery is read as a receipt, unless it is no image Nexo can store. */
    fun scanPickedPhoto(uri: Uri) {
        viewModelScope.launch {
            if (photos.isSupported(uri)) {
                scanReceipt(uri)
            } else {
                showMessage(R.string.unsupported_file_format)
            }
        }
    }

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
                form.update { s ->
                    if (s.scan?.uri == uri) {
                        s.copy(scan = ScanUi.Done(uri, read))
                    } else {
                        s
                    }
                }
            }
        }
    }

    fun dismissScan() {
        scanJob?.cancel()
        form.update { it.copy(scan = null) }
    }

    /**
     * The user's confirmed reading: [amount] (as shown or corrected, rounded to the entry's currency), its shop as the
     * note when [useMerchant] and the note is still empty, and the photo itself.
     * Nothing is saved until the entry is.
     */
    fun applyReceipt(amount: Double?, useMerchant: Boolean) {
        val done = form.value.scan as? ScanUi.Done ?: return
        edit { s ->
            s.withReceipt(done, amount, useMerchant, refCurrencyDecimals(s.currencyId))
        }
        advance()
    }

    fun setFeeExpanded(expanded: Boolean) {
        edit { s ->
            if (expanded) {
                s.copy(feeExpanded = true, feeCurrencyId = s.currencyId)
            } else {
                s.copy(feeExpanded = false, feeAmount = null)
            }
        }
        if (expanded) openPanel(Panel.FEE)
    }

    fun setRepeat(type: RepeatType) = edit { it.withRepeat(type) }

    fun setRepeatEnd(d: LocalDate?) = edit { it.copy(repeatEnd = d) }

    fun setInstallments(n: Int) = edit { it.withInstallments(n) }

    fun clearRepInst() = edit {
        it.copy(repeat = RepeatType.NONE, installments = 0, repeatEnd = null)
    }

    fun dismissRepeatConfirm() = form.update { it.copy(confirmRepeat = false) }
    fun setAskDelete(v: Boolean) = form.update { it.copy(askDelete = v) }

    /** Opens the first empty field in visual order. */
    private fun advance(fromPad: Boolean = false) {
        val s = form.value
        if (s.viewing) return
        val next = s.nextEmptyField()
        if (next != null) {
            if (fromPad && (next == Field.AMOUNT || next == Field.FEE)) {
                form.update { it.copy(panel = Panel.NONE) }
                return
            }
            openPanel(EntryForm.panelFor(next))
        } else {
            val focus = !s.filledOnce && s.note.isEmpty()
            form.update {
                it.copy(
                    panel = Panel.NONE,
                    filledOnce = true,
                    noteFocusRequest = if (focus) {
                        it.noteFocusRequest +
                            1
                    } else {
                        it.noteFocusRequest
                    },
                )
            }
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
        val accountCheck = {
            (s.accountId != null && r.accountById.containsKey(s.accountId)) ||
                fail(R.string.select_specific_asset, Panel.ACCOUNT)
        }
        val secondCheck = {
            if (s.isTransfer) {
                s.toAccountId != null ||
                    fail(R.string.select_specific_asset, Panel.TO_ACCOUNT)
            } else {
                s.isBalance || s.isBalanceAdjustment ||
                    (s.categoryId != null && r.categoryById.containsKey(s.categoryId)) ||
                    fail(R.string.select_specific_category, Panel.CATEGORY)
            }
        }
        if (accountFirst || s.isTransfer) {
            checks += accountCheck
            checks += secondCheck
        } else {
            checks +=
                secondCheck
            checks += accountCheck
        }
        if (!checks.all { it() }) return false
        if (s.isTransfer && s.accountId == s.toAccountId) {
            showMessage(R.string.same_account_transfer)
            return false
        }
        if (s.repeat != RepeatType.NONE &&
            s.date.isBefore(RepeatSchedule.oldestAllowed(s.repeat, LocalDate.now(clock)))
        ) {
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
            when (val result = saveEntry(entrySave(form.value))) {
                SaveEntryResult.Failed -> {
                    form.update { it.copy(saving = false) }
                    showMessage(R.string.save_failed)
                }

                is SaveEntryResult.Saved -> {
                    results.update { it + NavResult.TransactionSaved(result.id) }
                    afterSave(continueAfter, result.sameNoteAssigned)
                }
            }
        }
    }

    /** The form as it is saved, with the records this way of opening it settles. */
    private fun entrySave(s: EntryForm) = EntrySave(
        draft = s.toDraft(),
        smsReview = key.smsItemId?.takeIf { key.mode == EntryMode.SMS }?.let { id ->
            SmsReview(id, opened.smsItem, s.amount, s.feeAmount?.takeIf { s.feeExpanded && it > 0 })
        },
        estimateEdit = opened.linkedEstimate?.takeIf { key.mode == EntryMode.EDIT }?.let {
            EstimateEdit(it, savedAmount = opened.original?.amount, amount = s.amount)
        },
        uncategorizedOriginal = opened.original?.takeIf { key.reviewUnassigned },
        importLine = key.importFile?.takeIf { key.mode == EntryMode.IMPORT_FIX }?.let {
            ImportLine(it, key.importLine)
        },
    )

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
            val now = clock.millis()
            val rule = opened.originalRule
            repeats.save(
                s.toRepeatRule(
                    id = rule?.id ?: UUID.randomUUID().toString(),
                    currency = r.currency(s.currencyId),
                    createdAt = rule?.createdAt ?: now,
                    now = now,
                ),
            )
            close(s.date)
        }
    }

    private suspend fun afterSave(continueAfter: Boolean, sameNoteAssigned: Int) {
        val s = form.value
        if (key.reviewUnassigned) {
            if (sameNoteAssigned > 0) showMessage(R.string.bulk_assigned, sameNoteAssigned)
            openNextUnassigned()
            return
        }
        if (continueAfter) {
            val r = refs.getRefData()
            val fresh = loader.newEntry(key, r, loader.blank(r))
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
        val next = transactions.getUnassigned(s.date.toIsoText()).firstOrNull {
            it.id !=
                s.originalId
        }
        if (next == null) {
            close(s.date)
        } else {
            open(EntryKey(mode = EntryMode.EDIT, txId = next.id, reviewUnassigned = true))
        }
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
        val tx = opened.original ?: return
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

    private companion object {
        const val SAVED_FORM = "entryForm"
    }
}
