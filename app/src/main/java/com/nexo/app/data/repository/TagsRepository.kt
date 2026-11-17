package com.nexo.app.data.repository

import com.nexo.app.core.model.Tag
import kotlinx.coroutines.flow.Flow

sealed interface TagResult {
    data class Ok(val id: String) : TagResult
    data object Exists : TagResult
    data object Empty : TagResult
}

// Names are unique ignoring case.
interface TagsRepository {
    fun getTagsStream(): Flow<List<Tag>>

    suspend fun add(raw: String): TagResult

    suspend fun rename(id: String, raw: String): TagResult

    suspend fun usageCount(id: String): Int

    suspend fun delete(id: String)

    suspend fun reorder(ids: List<String>)

    companion object {
        const val MAX_LENGTH = 15
        private val forbidden =
            setOf('{', '}', '[', ']', '(', ')', '"', '\'', '‘', '’', '“', '”', '@', '#', ',')

        // Removes forbidden characters and every kind of whitespace.
        fun filter(input: String): String = input.filterNot {
            it in forbidden || it.isWhitespace() ||
                Character.isSpaceChar(it)
        }.take(MAX_LENGTH)

        fun normalize(input: String): String = filter(input.trim())
    }
}
