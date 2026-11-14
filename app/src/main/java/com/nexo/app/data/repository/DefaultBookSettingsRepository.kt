package com.nexo.app.data.repository

import com.nexo.app.core.model.BookSettings
import com.nexo.app.data.database.dao.SettingDao
import com.nexo.app.data.database.entity.SettingEntity
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultBookSettingsRepository @Inject constructor(
    private val settingDao: SettingDao,
) : BookSettingsRepository {
    override fun getBookSettingsStream(): Flow<BookSettings> =
        settingDao.observeAll().map { rows -> BookSettings.fromMap(rows.associate { it.key to it.value }) }

    override suspend fun updateBookSettings(transform: (BookSettings) -> BookSettings) {
        val current = BookSettings.fromMap(settingDao.getAll().associate { it.key to it.value })
        settingDao.putAll(transform(current).toMap().map { SettingEntity(it.key, it.value) })
    }
}
