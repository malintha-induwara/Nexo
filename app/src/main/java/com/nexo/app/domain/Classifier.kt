package com.nexo.app.domain

import com.nexo.app.core.model.CardAccounting
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.TxKind

/**
 * Income / expense classification. Transfers are judged on their outgoing leg:
 * [accountId] is the source and [counterpartId] the destination.
 * An account with no group (or "None") is treated as non-card in lump-sum mode.
 */
class Classifier(private val ref: RefData) {
    private val lumpSum = ref.settings.cardAccounting == CardAccounting.LUMP_SUM
    private val transferIncome = ref.settings.transferIncome

    fun classify(kind: TxKind, accountId: String?, counterpartId: String?): CashFlow = when (kind) {
        TxKind.INCOME -> CashFlow.INCOME
        TxKind.EXPENSE -> if (lumpSum && ref.isCard(accountId)) CashFlow.NEUTRAL else CashFlow.EXPENSE
        TxKind.TRANSFER_OUT -> classifyTransfer(accountId, counterpartId)
        else -> CashFlow.NEUTRAL
    }

    private fun classifyTransfer(src: String?, dst: String?): CashFlow {
        val srcFlag = ref.isFlagged(src)
        val dstFlag = ref.isFlagged(dst)
        val srcCard = ref.isCard(src)
        val dstCard = ref.isCard(dst)
        return if (transferIncome) {
            if (!lumpSum) {
                when {
                    srcFlag && !dstFlag -> CashFlow.INCOME
                    !srcFlag && dstFlag -> CashFlow.EXPENSE
                    else -> CashFlow.NEUTRAL
                }
            } else {
                when {
                    srcFlag && !dstFlag && !dstCard -> CashFlow.INCOME
                    !srcCard && !srcFlag && (dstFlag || dstCard) -> CashFlow.EXPENSE
                    else -> CashFlow.NEUTRAL
                }
            }
        } else {
            if (dstFlag || (lumpSum && dstCard)) CashFlow.EXPENSE else CashFlow.NEUTRAL
        }
    }

    /** True when a transfer counts on the stats screen as a transfer-expense row. */
    fun transferStatsFlow(src: String?, dst: String?): CashFlow {
        val srcFlag = ref.isFlagged(src)
        val dstFlag = ref.isFlagged(dst)
        return if (transferIncome) {
            when {
                !srcFlag && dstFlag -> CashFlow.EXPENSE
                srcFlag && !dstFlag -> CashFlow.INCOME
                else -> CashFlow.NEUTRAL
            }
        } else if (dstFlag) CashFlow.EXPENSE else CashFlow.NEUTRAL
    }
}

data class InOut(val income: Double = 0.0, val expense: Double = 0.0) {
    val total: Double get() = income - expense
    operator fun plus(other: InOut) = InOut(income + other.income, expense + other.expense)
}

/** Income/expense totals from aggregates or rows, in main currency. */
object Totals {
    fun fromAggregates(ref: RefData, rows: List<com.nexo.app.data.database.dao.TxAggregate>): InOut {
        val c = Classifier(ref)
        var inc = java.math.BigDecimal.ZERO
        var exp = java.math.BigDecimal.ZERO
        for (r in rows) {
            when (c.classify(r.kind, r.accountId, r.counterpartAccountId)) {
                CashFlow.INCOME -> inc += java.math.BigDecimal.valueOf(r.main)
                CashFlow.EXPENSE -> exp += java.math.BigDecimal.valueOf(r.main)
                CashFlow.NEUTRAL -> Unit
            }
        }
        return InOut(inc.toDouble(), exp.toDouble())
    }

    fun fromRows(ref: RefData, rows: List<com.nexo.app.data.database.entity.TransactionEntity>): InOut {
        val c = Classifier(ref)
        var inc = java.math.BigDecimal.ZERO
        var exp = java.math.BigDecimal.ZERO
        for (r in rows) {
            when (c.classify(r.kind, r.accountId, r.counterpartAccountId)) {
                CashFlow.INCOME -> inc += java.math.BigDecimal.valueOf(r.amountMain)
                CashFlow.EXPENSE -> exp += java.math.BigDecimal.valueOf(r.amountMain)
                CashFlow.NEUTRAL -> Unit
            }
        }
        return InOut(inc.toDouble(), exp.toDouble())
    }
}
