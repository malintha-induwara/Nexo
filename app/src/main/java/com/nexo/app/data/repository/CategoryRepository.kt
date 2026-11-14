package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.core.model.CategoryKind
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(private val db: NexoDatabase) {
    private fun now() = System.currentTimeMillis()

    suspend fun add(kind: CategoryKind, name: String, parentId: String?, emoji: String? = null, colorIndex: Int? = null): String {
        val id = UUID.randomUUID().toString()
        val order = db.categoryDao().maxOrder() + 1
        db.categoryDao().upsert(
            listOf(CategoryEntity(id, name.trim(), kind, parentId, order, createdAt = now(), updatedAt = now(), emoji = emoji?.takeIf { it.isNotBlank() }, colorIndex = colorIndex)),
        )
        return id
    }

    /** [emoji] and [colorIndex] are written as given; null means "derive automatically" (see CategoryVisuals). */
    suspend fun update(id: String, name: String, parentId: String?, emoji: String?, colorIndex: Int?) {
        val c = db.categoryDao().getById(id) ?: return
        db.categoryDao().upsert(
            listOf(c.copy(name = name.trim(), parentId = parentId, emoji = emoji?.takeIf { it.isNotBlank() }, colorIndex = colorIndex, updatedAt = now())),
        )
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

    suspend fun toMain(id: String) {
        val c = db.categoryDao().getById(id) ?: return
        db.categoryDao().upsert(listOf(c.copy(parentId = null, orderSeq = db.categoryDao().maxOrder() + 1, updatedAt = now())))
    }

    /** The source and all its subcategories move under [targetId]. */
    suspend fun toSub(sourceId: String, targetId: String) = db.withTransaction {
        if (sourceId == targetId) return@withTransaction
        val all = db.categoryDao().getAll()
        val moving = all.filter { it.id == sourceId || it.parentId == sourceId }
        db.categoryDao().upsert(moving.map { it.copy(parentId = targetId, orderSeq = 999, updatedAt = now()) })
    }
}
