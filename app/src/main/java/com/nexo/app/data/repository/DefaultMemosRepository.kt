package com.nexo.app.data.repository

import com.nexo.app.core.model.Memo
import com.nexo.app.data.database.dao.MemoDao
import com.nexo.app.data.database.entity.MemoEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultMemosRepository @Inject constructor(
    private val memoDao: MemoDao,
) : MemosRepository {
    override fun getMemosStream(start: String, end: String): Flow<List<Memo>> = memoDao.observeForRange(start, end).map { rows -> rows.map(MemoEntity::asExternalModel) }

    override suspend fun getMemos(): List<Memo> = memoDao.getAll().map(MemoEntity::asExternalModel)

    override suspend fun getMemo(id: String): Memo? = memoDao.getById(id)?.asExternalModel()

    override suspend fun nextOrder(): Int = memoDao.maxOrder() + 1

    override suspend fun save(memo: Memo) = memoDao.upsert(listOf(memo.asEntity()))

    override suspend fun delete(id: String) = memoDao.delete(id)

    override suspend fun search(start: String, end: String, pattern: String): List<Memo> = memoDao.search(start, end, pattern).map(MemoEntity::asExternalModel)
}
