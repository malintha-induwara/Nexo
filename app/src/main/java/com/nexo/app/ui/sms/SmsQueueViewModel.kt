package com.nexo.app.ui.sms

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.RefData
import com.nexo.app.sms.DenyReason
import com.nexo.app.sms.SmsStatus
import com.nexo.app.sms.approve.ApproveResult
import com.nexo.app.sms.approve.SmsApprover
import com.nexo.app.sms.capture.SmsCapture
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.needsAttention
import com.nexo.app.sms.needsDetails
import com.nexo.app.ui.components.UndoMessage
import com.nexo.app.work.Notifications
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import javax.inject.Inject

/** NQ-001 filter chips. The first two show the queue (pending items); the rest show decided items still kept. */
enum class QueueFilter(val label: Int, val status: SmsStatus) {
    ATTENTION(R.string.sms_filter_attention, SmsStatus.PENDING),
    ALL(R.string.sms_filter_all, SmsStatus.PENDING),
    DENIED(R.string.sms_filter_denied, SmsStatus.DENIED),
    EXPIRED(R.string.sms_filter_expired, SmsStatus.EXPIRED),
    CANCELLED(R.string.sms_filter_cancelled, SmsStatus.CANCELLED),
}

/** A bank card that pending items mention but no account is set for (NQ-032 "Which account is the card ending 1111?"). */
data class CardRef(val bank: String, val last4: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SmsQueueViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    db: NexoDatabase,
    refs: RefRepository,
    private val prefs: DevicePrefs,
    private val approver: SmsApprover,
    private val capture: SmsCapture,
) : ViewModel() {
    private val dao = db.smsDao()

    val ref: StateFlow<RefData> = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    val device = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _filter = MutableStateFlow(QueueFilter.ALL)
    val filter: StateFlow<QueueFilter> = _filter.asStateFlow()

    val pending: StateFlow<List<SmsItemEntity>> =
        dao.observeByStatus(listOf(SmsStatus.PENDING)).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Null while loading. */
    val items: StateFlow<List<SmsItemEntity>?> = _filter.flatMapLatest { f ->
        dao.observeByStatus(listOf(f.status)).map { list -> if (f == QueueFilter.ATTENTION) list.filter { it.needsAttention } else list }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unmappedCards: StateFlow<List<CardRef>> = pending.map { list ->
        list.filter { it.accountId == null && it.cardLast4 != null }.map { CardRef(it.bank, it.cardLast4!!) }.distinct()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    private val _undo = MutableStateFlow<UndoMessage?>(null)
    val undo: StateFlow<UndoMessage?> = _undo.asStateFlow()

    private val _permitted = MutableStateFlow(capture.hasPermission())
    val permitted: StateFlow<Boolean> = _permitted.asStateFlow()

    /** The queue is open: nothing is "new" any more, so the notification goes. */
    fun markSeen() = viewModelScope.launch {
        prefs.setSmsSeenAt(System.currentTimeMillis())
        Notifications.postSmsReview(context, 0)
        _permitted.value = capture.hasPermission()
    }

    fun setFilter(f: QueueFilter) {
        _filter.value = f
        _selection.value = emptySet()
    }

    fun approve(id: String) = viewModelScope.launch {
        when (approver.approve(id)) {
            is ApproveResult.Done -> show(context.getString(R.string.sms_approved))
            is ApproveResult.Incomplete -> show(context.getString(R.string.sms_needs_details_first))
            ApproveResult.Gone -> Unit
        }
    }

    fun deny(ids: Collection<String>, reason: DenyReason? = null) = viewModelScope.launch {
        val denied = approver.deny(ids, reason)
        _selection.value = emptySet()
        if (denied.isEmpty()) return@launch
        val restoreIds = denied.map { it.id }
        _undo.value = UndoMessage(
            context.resources.getQuantityString(R.plurals.sms_n_denied, denied.size, denied.size),
            context.getString(R.string.sms_undo),
        ) { restore(restoreIds) }
    }

    fun restore(ids: Collection<String>) = viewModelScope.launch { approver.restore(ids) }

    fun setAmount(id: String, value: Double) = viewModelScope.launch { approver.setAmount(id, value) }

    fun mapCard(card: CardRef, accountId: String) = viewModelScope.launch { approver.mapCard(card.bank, card.last4, accountId) }

    fun toggleSelect(id: String) = _selection.update { if (id in it) it - id else it + id }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun approveSelected() = viewModelScope.launch {
        val ids = _selection.value
        val ready = pending.value.filter { it.id in ids && !it.needsDetails }.map { it.id }
        val approved = approver.approveAll(ready)
        val skipped = ids.size - approved
        _selection.value = emptySet()
        val parts = buildList {
            if (approved > 0) add(context.resources.getQuantityString(R.plurals.sms_n_approved, approved, approved))
            if (skipped > 0) add(context.resources.getQuantityString(R.plurals.sms_n_skipped, skipped, skipped))
        }
        if (parts.isNotEmpty()) show(parts.joinToString(" · "))
    }

    fun assignAccount(accountId: String) = viewModelScope.launch {
        approver.setAccounts(_selection.value, accountId)
        _selection.value = emptySet()
    }

    fun dismissUndo() {
        _undo.value = null
    }

    private fun show(text: String) {
        _undo.value = UndoMessage(text)
    }
}
