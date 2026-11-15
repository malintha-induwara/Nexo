package com.nexo.app.data.sms

import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsOtpHint
import com.nexo.app.data.database.dao.SmsDao
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel

/** The live Review Queue as the correlator's store. */
class RoomSmsStore(private val dao: SmsDao) : SmsStore {
    override suspend fun byDedupeKey(key: String) = dao.byDedupeKey(key)?.asExternalModel()
    override suspend fun pendingRetry(
        bank: String,
        route: String,
        amount: Double,
        currency: String,
        since: Long,
    ) = dao.pendingRetry(bank, route, amount, currency, since)?.asExternalModel()
    override suspend fun upgradeCandidate(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
    ) = dao.upgradeCandidate(bank, amount, currency, since)?.asExternalModel()
    override suspend fun confirmedTransfer(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ) = dao.confirmedTransfer(bank, amount, currency, since, until)?.asExternalModel()
    override suspend fun purchasesOnCard(
        bank: String,
        cardLast4: String,
        since: Long,
        until: Long,
    ) = dao.purchasesOnCard(bank, cardLast4, since, until).map(SmsItemEntity::asExternalModel)
    override suspend fun insert(item: SmsItem) {
        dao.insert(item.asEntity())
    }
    override suspend fun update(item: SmsItem) = dao.update(item.asEntity())
    override suspend fun putHint(hint: SmsOtpHint) = dao.putHint(hint.asEntity())
    override suspend fun takeHint(
        bank: String,
        amount: Double,
        currency: String,
        since: Long,
        until: Long,
    ) = dao.takeHint(bank, amount, currency, since, until)?.asExternalModel()
}
