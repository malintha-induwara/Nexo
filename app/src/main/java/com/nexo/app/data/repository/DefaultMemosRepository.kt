package com.nexo.app.data.repository

import com.nexo.app.data.database.dao.MemoDao
import com.nexo.app.data.database.entity.MemoEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultMemosRepository @Inject constructor(
    private val memoDao: MemoDao,
) : MemosRepository {
    override fun getMemosStream(start: String, end: String): Flow<List<MemoEntity>> = memoDao.observeForRange(start, end)

    override suspend fun getMemos(): List<MemoEntity> = memoDao.getAll()

    override suspend fun getMemo(id: String): MemoEntity? = memoDao.getById(id)

    override suspend fun nextOrder(): Int = memoDao.maxOrder() + 1

    override suspend fun save(memo: MemoEntity) = memoDao.upsert(listOf(memo))

    override suspend fun delete(id: String) = memoDao.delete(id)

    override suspend fun search(start: String, end: String, pattern: String): List<MemoEntity> = memoDao.search(start, end, pattern)
}
