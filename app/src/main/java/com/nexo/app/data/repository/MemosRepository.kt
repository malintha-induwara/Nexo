package com.nexo.app.data.repository

import com.nexo.app.core.model.Memo
import kotlinx.coroutines.flow.Flow

interface MemosRepository {
    // ISO dates, inclusive.
    fun getMemosStream(start: String, end: String): Flow<List<Memo>>

    suspend fun getMemos(): List<Memo>

    suspend fun getMemo(id: String): Memo?

    suspend fun nextOrder(): Int

    suspend fun save(memo: Memo)

    suspend fun delete(id: String)

    suspend fun search(start: String, end: String, pattern: String): List<Memo>
}
