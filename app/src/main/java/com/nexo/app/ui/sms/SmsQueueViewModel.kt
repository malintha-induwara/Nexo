package com.nexo.app.ui.sms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.DenyReason
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
import com.nexo.app.ui.designsystem.component.UndoMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmsQueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sms: SmsRepository,
    refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
    private val getFeePlan: GetSmsFeePlanUseCase,
    private val approveSms: ApproveSmsUseCase,
    private val transactions: TransactionsRepository,
    private val inbox: SmsInboxRepository,
) : ViewModel() {

    val ref: StateFlow<RefData> = refs.getRefDataStream().stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.getDeviceSettingsStream().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _filter = MutableStateFlow(QueueFilter.ALL)
    val filter: StateFlow<QueueFilter> = _filter.asStateFlow()

    val pending: StateFlow<List<SmsItem>> =
        sms.getItemsStream(listOf(SmsStatus.PENDING)).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Null while loading. */
    val items: StateFlow<List<SmsItem>?> = _filter.flatMapLatest { f ->
        sms.getItemsStream(listOf(f.status)).map { list -> if (f == QueueFilter.ATTENTION) list.filter { it.needsAttention } else list }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unmappedCards: StateFlow<List<CardRef>> = pending.map { list ->
        list.filter { it.accountId == null && it.cardLast4 != null }.map { CardRef(it.bank, it.cardLast4!!) }.distinct()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _undo = MutableStateFlow<UndoMessage?>(null)
    val undo: StateFlow<UndoMessage?> = _undo.asStateFlow()

    private val _feePrompts = MutableStateFlow<List<FeePrompt>>(emptyList())

    /** The fee question on screen; the rest wait their turn. */
    val feePrompt: StateFlow<FeePrompt?> = _feePrompts.map { it.firstOrNull() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _permitted = MutableStateFlow(inbox.hasPermission())
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

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
            show(context.getString(R.string.sms_needs_details_first))
            return@launch
        }
        when (val plan = getFeePlan(item)) {
            FeePlan.None -> if (approveSms(id) is ApproveResult.Done) show(context.getString(R.string.sms_approved))
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
        if (approved > 0) show(context.resources.getQuantityString(R.plurals.sms_n_approved, approved, approved))
    }

    private fun autoFee(item: SmsItem, amount: Double, result: ApproveResult) {
        val feeId = (result as? ApproveResult.Done)?.feeId ?: return
        val text = AmountFormatter.format(amount, ref.value.accountCurrency(item.accountId))
        _undo.value = UndoMessage(context.getString(R.string.fee_auto_added, text), context.getString(R.string.sms_undo)) {
            viewModelScope.launch { transactions.delete(listOf(feeId)) }
        }
    }

    fun deny(ids: Collection<String>, reason: DenyReason? = null) = viewModelScope.launch {
        val denied = sms.deny(ids, reason)
        _selection.value = emptySet()
        if (denied.isEmpty()) return@launch
        val restoreIds = denied.map { it.id }
        _undo.value = UndoMessage(
            context.resources.getQuantityString(R.plurals.sms_n_denied, denied.size, denied.size),
            context.getString(R.string.sms_undo),
        ) { restore(restoreIds) }
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
            if (approved > 0) add(context.resources.getQuantityString(R.plurals.sms_n_approved, approved, approved))
            if (skipped > 0) add(context.resources.getQuantityString(R.plurals.sms_n_skipped, skipped, skipped))
        }
        if (parts.isNotEmpty()) show(parts.joinToString(" · "))
    }

    fun assignAccount(accountId: String) = viewModelScope.launch {
        sms.setAccounts(_selection.value, accountId)
        _selection.value = emptySet()
    }

    /** Re-enable hint dismissed. */
    fun clearGap() = viewModelScope.launch { prefs.clearSmsGap() }

    /** "Not now". The picker stays in Settings. */
    fun skipHistory() = viewModelScope.launch { prefs.setSmsHistoryImport(com.nexo.app.core.model.SmsHistoryImport.SKIPPED) }

    fun dismissUndo() {
        _undo.value = null
    }

    private fun show(text: String) {
        _undo.value = UndoMessage(text)
    }
}
