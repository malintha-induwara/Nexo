package com.nexo.app.data

import androidx.room.withTransaction
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.model.CategoryKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Category management (ST-061…ST-069). */
@Singleton
class CategoryRepository @Inject constructor(private val db: NexoDatabase) {
    private fun now() = System.currentTimeMillis()

    suspend fun add(kind: CategoryKind, name: String, parentId: String?): String {
        val id = UUID.randomUUID().toString()
        val order = db.categoryDao().maxOrder() + 1
        db.categoryDao().upsert(listOf(CategoryEntity(id, name.trim(), kind, parentId, order, createdAt = now(), updatedAt = now())))
        return id
    }

    suspend fun update(id: String, name: String, parentId: String?) {
        val c = db.categoryDao().getById(id) ?: return
        db.categoryDao().upsert(listOf(c.copy(name = name.trim(), parentId = parentId, updatedAt = now())))
    }

    /** Soft delete; a main category takes its subcategories with it. */
    suspend fun delete(id: String) = db.withTransaction {
        val all = db.categoryDao().getAll()
        val rows = all.filter { it.id == id || it.parentId == id }
        db.categoryDao().upsert(rows.map { it.copy(isDeleted = true, updatedAt = now()) })
    }

    suspend fun reorder(ids: List<String>) = db.withTransaction {
        val byId = db.categoryDao().getAll().associateBy { it.id }
        db.categoryDao().upsert(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(orderSeq = i, updatedAt = now()) })
    }

    /** ST-066. */
    suspend fun toMain(id: String) {
        val c = db.categoryDao().getById(id) ?: return
        db.categoryDao().upsert(listOf(c.copy(parentId = null, orderSeq = db.categoryDao().maxOrder() + 1, updatedAt = now())))
    }

    /** ST-067: the source and all its subcategories move under [targetId]. */
    suspend fun toSub(sourceId: String, targetId: String) = db.withTransaction {
        if (sourceId == targetId) return@withTransaction
        val all = db.categoryDao().getAll()
        val moving = all.filter { it.id == sourceId || it.parentId == sourceId }
        db.categoryDao().upsert(moving.map { it.copy(parentId = targetId, orderSeq = 999, updatedAt = now()) })
    }
}
