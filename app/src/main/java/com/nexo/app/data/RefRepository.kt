package com.nexo.app.data

import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.SettingEntity
import com.nexo.app.core.settings.BookSettings
import com.nexo.app.di.ApplicationScope
import com.nexo.app.domain.RefData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/** Reactive snapshot of all reference tables plus the book settings. */
@Singleton
class RefRepository @Inject constructor(
    private val db: NexoDatabase,
    @ApplicationScope scope: CoroutineScope,
) {
    private val settingsFlow: Flow<BookSettings> =
        db.settingDao().observeAll().map { rows -> BookSettings.fromMap(rows.associate { it.key to it.value }) }

    val ref: SharedFlow<RefData> = combine(
        combine(db.accountGroupDao().observeAll(), db.accountDao().observeAll(), db.categoryDao().observeAll()) { g, a, c -> Triple(g, a, c) },
        combine(db.currencyDao().observeAll(), db.tagDao().observeAll(), settingsFlow) { cur, t, s -> Triple(cur, t, s) },
    ) { (g, a, c), (cur, t, s) ->
        RefData(groups = g, accounts = a, categories = c, currencies = cur, tags = t, settings = s)
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    val settings: Flow<BookSettings> = ref.map { it.settings }

    suspend fun snapshot(): RefData = ref.first()

    suspend fun updateSettings(transform: (BookSettings) -> BookSettings) {
        val current = BookSettings.fromMap(db.settingDao().getAll().associate { it.key to it.value })
        val next = transform(current)
        db.settingDao().putAll(next.toMap().map { SettingEntity(it.key, it.value) })
    }
}
