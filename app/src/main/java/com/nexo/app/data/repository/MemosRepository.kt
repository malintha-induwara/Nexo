package com.nexo.app.data.repository

import com.nexo.app.data.database.entity.MemoEntity
import kotlinx.coroutines.flow.Flow

/** Dated notes shown alongside the ledger. */
interface MemosRepository {
    /** Memos dated from [start] to [end] (ISO dates, inclusive). */
    fun getMemosStream(start: String, end: String): Flow<List<MemoEntity>>

    suspend fun getMemos(): List<MemoEntity>

    suspend fun getMemo(id: String): MemoEntity?

    /** Order value that puts a new memo last. */
    suspend fun nextOrder(): Int

    suspend fun save(memo: MemoEntity)

    suspend fun delete(id: String)

    suspend fun search(start: String, end: String, pattern: String): List<MemoEntity>
}
