package com.nexo.app.testing

import com.nexo.app.core.model.Memo
import com.nexo.app.data.repository.MemosRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Memos kept in a list; the order and search rules are the simple ones the screens rely on. */
class FakeMemosRepository(initial: List<Memo> = emptyList()) : MemosRepository {
    private val memos = MutableStateFlow(initial)

    val current: List<Memo> get() = memos.value

    override fun getMemosStream(start: String, end: String): Flow<List<Memo>> =
        memos.map { list -> list.filter { it.date in start..end } }

    override suspend fun getMemos(): List<Memo> = memos.value

    override suspend fun getMemo(id: String): Memo? = memos.value.firstOrNull { it.id == id }

    override suspend fun nextOrder(): Int = (memos.value.maxOfOrNull { it.orderSeq } ?: 0) + 1

    override suspend fun save(memo: Memo) = memos.update { list -> list.filter { it.id != memo.id } + memo }

    override suspend fun delete(id: String) = memos.update { list -> list.filter { it.id != id } }

    override suspend fun search(start: String, end: String, pattern: String): List<Memo> =
        memos.value.filter { it.date in start..end && (pattern in it.title || pattern in it.content) }
}
