package com.nexo.app.ui.sms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsInboxRepository
import com.nexo.app.data.repository.SmsRepository
import com.nexo.app.data.repository.TransactionsRepository
import com.nexo.app.data.repository.UserPreferencesRepository
import com.nexo.app.data.sms.needsAttention
import com.nexo.app.data.sms.needsDetails
import com.nexo.app.domain.sms.ApproveResult
import com.nexo.app.domain.sms.ApproveSmsUseCase
import com.nexo.app.domain.sms.FeePlan
import com.nexo.app.domain.sms.GetSmsFeePlanUseCase
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.joinToUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Filter chips. The first two show the queue (pending items); the rest show decided items still kept. */
enum class QueueFilter(val label: Int, val status: SmsStatus) {
    ATTENTION(R.string.sms_filter_attention, SmsStatus.PENDING),
    ALL(R.string.sms_filter_all, SmsStatus.PENDING),
    DENIED(R.string.sms_filter_denied, SmsStatus.DENIED),
    EXPIRED(R.string.sms_filter_expired, SmsStatus.EXPIRED),
    CANCELLED(R.string.sms_filter_cancelled, SmsStatus.CANCELLED),
}

/** One fee question, for a single item or a bulk-approved batch on the same route. */
data class FeePrompt(val items: List<SmsItem>, val plan: FeePlan.Ask)

/** A bank card that pending items mention but no account is set for. */
data class CardRef(val bank: String, val last4: String)

/** What the Undo button of a [QueueNotice] reverses. */
sealed interface QueueUndo {
    data class RemoveFee(val feeId: String) : QueueUndo

    data class Restore(val ids: List<String>) : QueueUndo
}

/** The message bar under the queue; a new [id] restarts its timer even with the same text. */
data class QueueNotice(val text: UiText, val undo: QueueUndo? = null, val id: Long = System.nanoTime())

data class SmsQueueUiState(
    val ref: RefData = RefData(),
    val device: DeviceSettings? = null,
    val filter: QueueFilter = QueueFilter.ALL,
    val pending: List<SmsItem> = emptyList(),
    /** Null while loading. */
    val items: List<SmsItem>? = null,
    val unmappedCards: List<CardRef> = emptyList(),
    val selection: Set<String> = emptySet(),
    val notice: QueueNotice? = null,
    /** The fee question on screen; the rest wait their turn. */
    val feePrompt: FeePrompt? = null,
    val permitted: Boolean = false,
)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmsQueueViewModel @Inject constructor(
    private val sms: SmsRepository,
    refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
    private val getFeePlan: GetSmsFeePlanUseCase,
    private val approveSms: ApproveSmsUseCase,
    private val transactions: TransactionsRepository,
    private val inbox: SmsInboxRepository,
) : ViewModel() {

    /** Kept for event handlers that need these synchronously. */
    private val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    private val pending: StateFlow<List<SmsItem>> =
        sms.getItemsStream(listOf(SmsStatus.PENDING)).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow(QueueFilter.ALL)
    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    private val _notice = MutableStateFlow<QueueNotice?>(null)
    private val _feePrompts = MutableStateFlow<List<FeePrompt>>(emptyList())
    private val _permitted = MutableStateFlow(inbox.hasPermission())

    private val items: Flow<List<SmsItem>?> = _filter.flatMapLatest { f ->
        sms.getItemsStream(listOf(f.status)).map { list -> if (f == QueueFilter.ATTENTION) list.filter { it.needsAttention } else list }
    }

    val uiState: StateFlow<SmsQueueUiState> = combine(
        combine(ref, prefs.getDeviceSettingsStream(), _filter, ::Triple),
        combine(pending, items, ::Pair),
        combine(_selection, _notice, _feePrompts, _permitted) { sel, notice, prompts, permitted -> Quad(sel, notice, prompts.firstOrNull(), permitted) },
    ) { (r, device, filter), (pendingItems, list), (sel, notice, prompt, permitted) ->
        val unmapped = pendingItems.filter { it.accountId == null && it.cardLast4 != null }.map { CardRef(it.bank, it.cardLast4!!) }.distinct()
        SmsQueueUiState(r, device, filter, pendingItems, list, unmapped, sel, notice, prompt, permitted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SmsQueueUiState(permitted = inbox.hasPermission()))

    /** The queue is open: nothing is "new" any more, so the notification goes. */
    fun markSeen() = viewModelScope.launch {
        sms.markQueueSeen()
        _permitted.value = inbox.hasPermission()
    }

    fun setFilter(f: QueueFilter) {
        _filter.value = f
        _selection.value = emptySet()
    }

    /** An item on a fee route asks first, unless its preset says always or never. */
    fun approve(id: String) = viewModelScope.launch {
        val item = pending.value.firstOrNull { it.id == id } ?: return@launch
        if (item.needsDetails) {
            show(UiText.of(R.string.sms_needs_details_first))
            return@launch
        }
        when (val plan = getFeePlan(item)) {
            FeePlan.None -> if (approveSms(id) is ApproveResult.Done) show(UiText.of(R.string.sms_approved))
            is FeePlan.Auto -> autoFee(item, plan.amount, approveSms(id, FeeChoice(plan.amount)))
            is FeePlan.Ask -> _feePrompts.update { it + FeePrompt(listOf(item), plan) }
        }
    }

    /** The answer to the fee question on screen; null is Cancel, which leaves the items in the queue. */
    fun answerFee(choice: FeeChoice?) = viewModelScope.launch {
        val prompt = _feePrompts.value.firstOrNull() ?: return@launch
        _feePrompts.update { it.drop(1) }
        if (choice == null) return@launch
        val approved = prompt.items.count { approveSms(it.id, choice) is ApproveResult.Done }
        if (approved > 0) show(UiText.plural(R.plurals.sms_n_approved, approved))
    }

    private fun autoFee(item: SmsItem, amount: Double, result: ApproveResult) {
        val feeId = (result as? ApproveResult.Done)?.feeId ?: return
        val text = AmountFormatter.format(amount, ref.value.accountCurrency(item.accountId))
        _notice.value = QueueNotice(UiText.of(R.string.fee_auto_added, text), QueueUndo.RemoveFee(feeId))
    }

    fun deny(ids: Collection<String>, reason: DenyReason? = null) = viewModelScope.launch {
        val denied = sms.deny(ids, reason)
        _selection.value = emptySet()
        if (denied.isEmpty()) return@launch
        val restoreIds = denied.map { it.id }
        _notice.value = QueueNotice(UiText.plural(R.plurals.sms_n_denied, denied.size), QueueUndo.Restore(restoreIds))
    }

    fun restore(ids: Collection<String>) = viewModelScope.launch { sms.restore(ids) }

    fun setAmount(id: String, value: Double) = viewModelScope.launch { sms.setAmount(id, value) }

    fun mapCard(card: CardRef, accountId: String) = viewModelScope.launch { sms.mapCard(card.bank, card.last4, accountId) }

    fun toggleSelect(id: String) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /**
     * Bulk approve: "always" and "never" presets apply silently; routes still on "ask" get one question each for
     * the whole batch. Items that need details stay in the queue.
     */
    fun approveSelected() = viewModelScope.launch {
        val ids = _selection.value
        val ready = pending.value.filter { it.id in ids && !it.needsDetails }
        _selection.value = emptySet()
        var approved = 0
        val asks = LinkedHashMap<FeeRoute, MutableList<Pair<SmsItem, FeePlan.Ask>>>()
        val prompts = ArrayList<FeePrompt>()
        for (item in ready) {
            when (val plan = getFeePlan(item)) {
                FeePlan.None -> if (approveSms(item.id) is ApproveResult.Done) approved++
                is FeePlan.Auto -> if (approveSms(item.id, FeeChoice(plan.amount)) is ApproveResult.Done) approved++
                // Each FX difference is its own amount, so those are asked one by one.
                is FeePlan.Ask -> if (plan.fxDifference) prompts += FeePrompt(listOf(item), plan) else asks.getOrPut(plan.route) { ArrayList() } += item to plan
            }
        }
        asks.values.forEach { group -> prompts += FeePrompt(group.map { it.first }, group.first().second) }
        _feePrompts.update { it + prompts }
        val skipped = ids.size - ready.size
        val parts = buildList {
            if (approved > 0) add(UiText.plural(R.plurals.sms_n_approved, approved))
            if (skipped > 0) add(UiText.plural(R.plurals.sms_n_skipped, skipped))
        }
        if (parts.isNotEmpty()) show(parts.joinToUiText(" · "))
    }

    fun assignAccount(accountId: String) = viewModelScope.launch {
        sms.setAccounts(_selection.value, accountId)
        _selection.value = emptySet()
    }

    /** Re-enable hint dismissed. */
    fun clearGap() = viewModelScope.launch { prefs.clearSmsGap() }

    /** "Not now". The picker stays in Settings. */
    fun skipHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.model.SmsHistoryImport.SKIPPED) }

    /** Undo pressed on [notice]. */
    fun undo(notice: QueueNotice) {
        when (val action = notice.undo) {
            is QueueUndo.RemoveFee -> viewModelScope.launch { transactions.delete(listOf(action.feeId)) }
            is QueueUndo.Restore -> restore(action.ids)
            null -> Unit
        }
        dismissNotice()
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private fun show(text: UiText) {
        _notice.value = QueueNotice(text)
    }
}
