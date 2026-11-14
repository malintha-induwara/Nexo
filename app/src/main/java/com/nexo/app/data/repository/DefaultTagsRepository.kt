package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.core.model.Tag
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.TagEntity
import com.nexo.app.data.database.entity.asExternalModel
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultTagsRepository @Inject constructor(private val db: NexoDatabase) : TagsRepository {
    override fun getTagsStream(): Flow<List<Tag>> = db.tagDao().observeAll().map { rows -> rows.map(TagEntity::asExternalModel) }

    override suspend fun add(raw: String): TagResult = db.withTransaction {
        val name = TagsRepository.normalize(raw)
        if (name.isEmpty()) return@withTransaction TagResult.Empty
        val all = db.tagDao().getAll()
        val match = all.firstOrNull { it.name.lowercase(Locale.ROOT) == name.lowercase(Locale.ROOT) }
        val order = (db.tagDao().maxOrder() + 1).coerceAtLeast(0)
        val now = System.currentTimeMillis()
        when {
            match != null && !match.isDeleted -> TagResult.Exists
            match != null -> {
                db.tagDao().upsert(listOf(match.copy(name = name, isDeleted = false, orderSeq = order, updatedAt = now)))
                TagResult.Ok(match.id)
            }
            else -> {
                val id = UUID.randomUUID().toString()
                db.tagDao().upsert(listOf(TagEntity(id, name, order, false, now)))
                TagResult.Ok(id)
            }
        }
    }

    override suspend fun rename(id: String, raw: String): TagResult = db.withTransaction {
        val name = TagsRepository.normalize(raw)
        if (name.isEmpty()) return@withTransaction TagResult.Empty
        val all = db.tagDao().getAll()
        if (all.any { it.name.equals(name, ignoreCase = true) }) return@withTransaction TagResult.Exists
        val tag = all.firstOrNull { it.id == id } ?: return@withTransaction TagResult.Empty
        db.tagDao().upsert(listOf(tag.copy(name = name, updatedAt = System.currentTimeMillis())))
        TagResult.Ok(id)
    }

    override suspend fun usageCount(id: String): Int = db.tagDao().usageCount(id)

    override suspend fun delete(id: String) {
        val tag = db.tagDao().getAll().firstOrNull { it.id == id } ?: return
        db.tagDao().upsert(listOf(tag.copy(isDeleted = true, updatedAt = System.currentTimeMillis())))
    }

    override suspend fun reorder(ids: List<String>) = db.withTransaction {
        val byId = db.tagDao().getAll().associateBy { it.id }
        db.tagDao().upsert(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(orderSeq = i) })
    }

}
