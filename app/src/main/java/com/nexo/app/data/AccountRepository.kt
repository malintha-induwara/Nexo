package com.nexo.app.data

import androidx.room.withTransaction
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.AccountGroupEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.domain.Conversion
import com.nexo.app.widget.WidgetRefresher
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Accounts and account groups. */
@Singleton
class AccountRepository @Inject constructor(
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val widgets: WidgetRefresher,
) {
    private fun now() = System.currentTimeMillis()

    suspend fun saveGroup(id: String?, name: String, type: GroupType) {
        val existing = id?.let { db.accountGroupDao().getById(it) }
        val row = existing?.copy(name = name.trim(), type = if (existing.builtinId != null && existing.builtinId in setOf(1, 2, 3, 11)) existing.type else type, updatedAt = now())
            ?: AccountGroupEntity(UUID.randomUUID().toString(), name.trim(), type, null, db.accountGroupDao().maxOrder() + 1, updatedAt = now())
        db.accountGroupDao().upsert(listOf(row))
    }

    suspend fun setGroupStatus(id: String, status: GroupStatus) {
        val g = db.accountGroupDao().getById(id) ?: return
        if (g.builtinId in setOf(1, 2, 3, 11) && status != GroupStatus.ACTIVE) return
        db.accountGroupDao().upsert(listOf(g.copy(status = status, updatedAt = now())))
        widgets.refresh()
    }

    suspend fun reorderGroups(ids: List<String>) = db.withTransaction {
        val all = db.accountGroupDao().getAll().associateBy { it.id }
        db.accountGroupDao().upsert(ids.mapIndexedNotNull { i, id -> all[id]?.copy(orderSeq = i + 1, updatedAt = now()) })
    }

    suspend fun nextAccountOrder(): Int = db.accountDao().maxOrder() + 1

    /** Saves an account; a currency change re-derives account amounts of its rows and linked debit cards. */
    suspend fun saveAccount(account: AccountEntity) {
        val ref = refs.snapshot()
        db.withTransaction {
            val old = db.accountDao().getById(account.id)
            db.accountDao().upsert(listOf(account.copy(updatedAt = now())))
            if (old != null && old.currencyId != account.currencyId) {
                val debits = ref.linkedDebitCards(account.id).map { it.copy(currencyId = account.currencyId, updatedAt = now()) }
                db.accountDao().upsert(debits)
                val newCur = ref.currency(account.currencyId)
                val ids = listOf(account.id) + debits.map { it.id }
                val rows = db.transactionDao().getAllForAccounts(ids)
                db.transactionDao().updateAll(
                    rows.map { r ->
                        r.copy(amountAccount = Conversion.accountAmount(r.amount, r.amountMain, ref.currency(r.currencyId), newCur, ref.mainCurrency), updatedAt = now())
                    },
                )
            }
        }
        widgets.refresh()
    }

    suspend fun setStatus(id: String, status: AccountStatus) {
        val a = db.accountDao().getById(id) ?: return
        db.accountDao().upsert(listOf(a.copy(status = status, updatedAt = now())))
        widgets.refresh()
    }

    /** Order = position in the whole list, group may change. */
    suspend fun reorderAccounts(order: List<Pair<String, String>>) = db.withTransaction {
        val all = db.accountDao().getAll().associateBy { it.id }
        db.accountDao().upsert(order.mapIndexedNotNull { i, (id, group) -> all[id]?.copy(orderSeq = i, groupId = group, updatedAt = now()) })
    }

    suspend fun setTransferExpense(id: String, flag: Boolean) {
        val a = db.accountDao().getById(id) ?: return
        db.accountDao().upsert(listOf(a.copy(transferExpense = flag, updatedAt = now())))
        widgets.refresh()
    }
}
