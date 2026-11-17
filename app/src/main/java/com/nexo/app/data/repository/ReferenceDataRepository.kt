package com.nexo.app.data.repository

import com.nexo.app.core.model.RefData
import kotlinx.coroutines.flow.Flow

// Reactive snapshot of every reference table plus the book settings.
interface ReferenceDataRepository {
    // Replays the latest snapshot to new collectors.
    fun getRefDataStream(): Flow<RefData>

    suspend fun getRefData(): RefData
}
