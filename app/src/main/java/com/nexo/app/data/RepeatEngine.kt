package com.nexo.app.data

import androidx.room.withTransaction
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.database.entity.RepeatRuleEntity
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.Money
import com.nexo.app.core.period.toIsoText
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.domain.Conversion
import com.nexo.app.domain.RefData
import com.nexo.app.domain.RepeatSchedule
import com.nexo.app.domain.RepeatWindow
import com.nexo.app.widget.WidgetRefresher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Materialises repeat rules into transactions (TX-020). Only one run at a time. */
@Singleton
class RepeatEngine @Inject constructor(
    private val db: NexoDatabase,
    private val refs: RefRepository,
    private val prefs: DevicePrefs,
    private val widgets: WidgetRefresher,
) {
    private val mutex = Mutex()

    /** Runs at most once per calendar day unless [force] is set. */
    suspend fun runIfNewDay(force: Boolean = false) {
        val today = LocalDate.now().toIsoText()
        if (!force && prefs.current().repeatDay == today) return
        run()
        prefs.setRepeatDay(today)
    }

    suspend fun run(today: LocalDate = LocalDate.now()): Int = mutex.withLock {
        val ref = refs.snapshot()
        val rules = db.repeatRuleDao().getAll()
        val period = ref.settings.periods.monthPeriodContaining(today)
        val window = RepeatWindow.of(today, ref.settings.repeatReflect == RepeatReflect.FIRST_OF_PERIOD, period.end)
        var created = 0
        val order = mapOf(TxKind.INCOME to 0, TxKind.EXPENSE to 1, TxKind.TRANSFER_OUT to 2)
        val sorted = rules.sortedWith(compareBy({ order[it.kind] ?: 3 }, { it.nextDate }))
        db.withTransaction {
            for (rule in sorted) {
                if (created >= MAX_PER_RUN) break
                if (rule.repeatType == RepeatType.NONE) continue
                var next: LocalDate? = runCatching { LocalDate.parse(rule.nextDate) }.getOrNull() ?: continue
                if (!(next!!.isAfter(window.lowerExclusive) && next.isBefore(window.horizonExclusive))) continue
                if (rule.kind != TxKind.TRANSFER_OUT) {
                    val cat = rule.categoryId?.let { ref.categoryById[it] }
                    if (cat == null || cat.isDeleted) continue
                }
                if (rule.repeatType == RepeatType.END_OF_MONTH) next = next.with(TemporalAdjusters.lastDayOfMonth())
                val end = rule.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val rows = ArrayList<TransactionEntity>()
                while (next != null && next.isBefore(window.horizonExclusive) && created < MAX_PER_RUN) {
                    if (end != null && next.isAfter(end)) break
                    rows += instances(ref, rule, next)
                    created++
                    next = RepeatSchedule.next(next, rule.repeatType, rule.anchorDay)
                }
                if (rows.isNotEmpty()) db.transactionDao().insertIgnore(rows)
                if (next != null) {
                    db.repeatRuleDao().upsert(listOf(rule.copy(nextDate = next.toIsoText(), updatedAt = System.currentTimeMillis())))
                }
            }
        }
        if (created > 0) widgets.refresh()
        created
    }

    private fun instances(ref: RefData, rule: RepeatRuleEntity, date: LocalDate): List<TransactionEntity> {
        val cur = ref.currency(rule.currencyId)
        val main = ref.mainCurrency
        val mainAmount = if (cur.id == main.id) rule.amount else Money.round(Money.multiply(rule.amount, Conversion.effectiveRate(cur)), main.decimals)
        val now = System.currentTimeMillis()
        val ms = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val base = "${rule.id}_${date.toIsoText()}"
        fun row(kind: TxKind, account: String?, counterpart: String?, group: String?) = TransactionEntity(
            id = "${base}_${kind.code}",
            kind = kind,
            accountId = account,
            counterpartAccountId = counterpart,
            categoryId = if (kind.isTransfer) null else rule.categoryId,
            dateTime = ms,
            date = date.toIsoText(),
            amount = rule.amount,
            amountMain = mainAmount,
            amountAccount = Conversion.accountAmount(rule.amount, mainAmount, cur, ref.accountCurrency(account), main),
            currencyId = cur.id,
            note = rule.note,
            description = rule.description,
            transferGroupId = group,
            repeatRuleId = rule.id,
            repeatType = rule.repeatType,
            createdAt = now,
            updatedAt = now,
        )
        return if (rule.kind == TxKind.TRANSFER_OUT) {
            val group = UUID.randomUUID().toString()
            listOf(
                row(TxKind.TRANSFER_OUT, rule.accountId, rule.toAccountId, group),
                row(TxKind.TRANSFER_IN, rule.toAccountId, rule.accountId, group),
            )
        } else {
            listOf(row(rule.kind, rule.accountId, null, null))
        }
    }

    companion object {
        const val MAX_PER_RUN = 200
    }
}
