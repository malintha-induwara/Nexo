package com.nexo.app.ui.sms

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.sms.needsAttention
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Pending count, and whether any of them needs attention (home badge and dot). */
data class QueueBadge(val count: Int = 0, val attention: Boolean = false)

@HiltViewModel
class SmsBadgeViewModel @Inject constructor(db: NexoDatabase) : ViewModel() {
    val badge = db.smsDao().observeByStatus(listOf(SmsStatus.PENDING))
        .map { list -> QueueBadge(list.size, list.any { it.needsAttention }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QueueBadge())
}
