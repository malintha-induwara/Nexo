package com.nexo.app.data.repository

import androidx.room.withTransaction
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.TagEntity
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TagResult {
    data class Ok(val id: String) : TagResult
    data object Exists : TagResult
    data object Empty : TagResult
}

@Singleton
class TagRepository @Inject constructor(private val db: NexoDatabase) {
    val tags = db.tagDao().observeAll()

    suspend fun add(raw: String): TagResult = db.withTransaction {
        val name = normalize(raw)
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

    suspend fun rename(id: String, raw: String): TagResult = db.withTransaction {
        val name = normalize(raw)
        if (name.isEmpty()) return@withTransaction TagResult.Empty
        val all = db.tagDao().getAll()
        if (all.any { it.name.equals(name, ignoreCase = true) }) return@withTransaction TagResult.Exists
        val tag = all.firstOrNull { it.id == id } ?: return@withTransaction TagResult.Empty
        db.tagDao().upsert(listOf(tag.copy(name = name, updatedAt = System.currentTimeMillis())))
        TagResult.Ok(id)
    }

    suspend fun usageCount(id: String): Int = db.tagDao().usageCount(id)

    suspend fun delete(id: String) {
        val tag = db.tagDao().getAll().firstOrNull { it.id == id } ?: return
        db.tagDao().upsert(listOf(tag.copy(isDeleted = true, updatedAt = System.currentTimeMillis())))
    }

    suspend fun reorder(ids: List<String>) = db.withTransaction {
        val byId = db.tagDao().getAll().associateBy { it.id }
        db.tagDao().upsert(ids.mapIndexedNotNull { i, id -> byId[id]?.copy(orderSeq = i) })
    }

    companion object {
        const val MAX_LENGTH = 15
        private val forbidden = setOf('{', '}', '[', ']', '(', ')', '"', '\'', '‘', '’', '“', '”', '@', '#', ',')

        /** Removes forbidden characters and every kind of whitespace. */
        fun filter(input: String): String = input.filterNot { it in forbidden || it.isWhitespace() || Character.isSpaceChar(it) }.take(MAX_LENGTH)

        fun normalize(input: String): String = filter(input.trim())
    }
}
