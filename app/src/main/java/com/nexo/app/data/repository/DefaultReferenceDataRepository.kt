package com.nexo.app.data.repository

import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.model.RefData
import com.nexo.app.data.database.dao.AccountDao
import com.nexo.app.data.database.dao.AccountGroupDao
import com.nexo.app.data.database.dao.CategoryDao
import com.nexo.app.data.database.dao.CurrencyDao
import com.nexo.app.data.database.dao.TagDao
import com.nexo.app.data.database.entity.AccountEntity
import com.nexo.app.data.database.entity.AccountGroupEntity
import com.nexo.app.data.database.entity.CategoryEntity
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.TagEntity
import com.nexo.app.data.database.entity.asExternalModel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn

@Singleton
class DefaultReferenceDataRepository @Inject constructor(
    accountGroupDao: AccountGroupDao,
    accountDao: AccountDao,
    categoryDao: CategoryDao,
    currencyDao: CurrencyDao,
    tagDao: TagDao,
    bookSettings: BookSettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : ReferenceDataRepository {
    private val refData: SharedFlow<RefData> = combine(
        combine(accountGroupDao.observeAll(), accountDao.observeAll(), categoryDao.observeAll()) {
                g,
                a,
                c,
            ->
            Triple(g, a, c)
        },
        combine(
            currencyDao.observeAll(),
            tagDao.observeAll(),
            bookSettings.getBookSettingsStream(),
        ) {
                cur,
                t,
                s,
            ->
            Triple(cur, t, s)
        },
    ) { (g, a, c), (cur, t, s) ->
        RefData(
            groups = g.map(AccountGroupEntity::asExternalModel),
            accounts = a.map(AccountEntity::asExternalModel),
            categories = c.map(CategoryEntity::asExternalModel),
            currencies = cur.map(CurrencyEntity::asExternalModel),
            tags = t.map(TagEntity::asExternalModel),
            settings = s,
        )
    }.shareIn(scope, SharingStarted.Eagerly, replay = 1)

    override fun getRefDataStream(): Flow<RefData> = refData

    override suspend fun getRefData(): RefData = refData.first()
}
