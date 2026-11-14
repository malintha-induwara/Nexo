package com.nexo.app.data.sms

import com.nexo.app.data.database.dao.SmsDao
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.database.entity.SmsOtpHintEntity

/** The live Review Queue as the correlator's store. */
class RoomSmsStore(private val dao: SmsDao) : SmsStore {
    override suspend fun byDedupeKey(key: String) = dao.byDedupeKey(key)
    override suspend fun pendingRetry(bank: String, route: String, amount: Double, currency: String, since: Long) =
        dao.pendingRetry(bank, route, amount, currency, since)
    override suspend fun upgradeCandidate(bank: String, amount: Double, currency: String, since: Long) =
        dao.upgradeCandidate(bank, amount, currency, since)
    override suspend fun purchasesOnCard(bank: String, cardLast4: String, since: Long, until: Long) =
        dao.purchasesOnCard(bank, cardLast4, since, until)
    override suspend fun insert(item: SmsItemEntity) {
        dao.insert(item)
    }
    override suspend fun update(item: SmsItemEntity) = dao.update(item)
    override suspend fun putHint(hint: SmsOtpHintEntity) = dao.putHint(hint)
    override suspend fun takeHint(bank: String, amount: Double, currency: String, since: Long, until: Long) =
        dao.takeHint(bank, amount, currency, since, until)
}
