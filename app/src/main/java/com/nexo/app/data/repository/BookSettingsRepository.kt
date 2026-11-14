package com.nexo.app.data.repository

import com.nexo.app.core.model.BookSettings
import kotlinx.coroutines.flow.Flow

/** Book settings: the key/value rows that travel with backups. */
interface BookSettingsRepository {
    fun getBookSettingsStream(): Flow<BookSettings>

    /** Reads the stored settings, applies [transform] and writes every key back. */
    suspend fun updateBookSettings(transform: (BookSettings) -> BookSettings)
}
