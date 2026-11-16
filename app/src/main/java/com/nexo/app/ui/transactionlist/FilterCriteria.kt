package com.nexo.app.ui.transactionlist

import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.Transaction
import com.nexo.app.core.model.TxKind
import com.nexo.app.domain.Classifier
import kotlinx.serialization.Serializable

/** Which categories and accounts a list of transactions is narrowed to. */
@Serializable
data class FilterCriteria(
    val incomeAll: Boolean = false,
    val expenseAll: Boolean = false,
    /** Main or sub category ids. */
    val categories: Set<String> = emptySet(),
    /** Main category ids whose "Other" (no subcategory) entry is selected. */
    val otherOf: Set<String> = emptySet(),
    val noneIncome: Boolean = false,
    val noneExpense: Boolean = false,
    val modifiedIncome: Boolean = false,
    val modifiedExpense: Boolean = false,
    /** Transfer-expense accounts: transfers into them (expense side) / out of them (income side). */
    val teExpense: Set<String> = emptySet(),
    val teIncome: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
) {
    val hasCategoryPart: Boolean
        get() = incomeAll || expenseAll || categories.isNotEmpty() || otherOf.isNotEmpty() ||
            noneIncome ||
            noneExpense ||
            modifiedIncome || modifiedExpense || teExpense.isNotEmpty() || teIncome.isNotEmpty()
    val active: Boolean get() = hasCategoryPart || accounts.isNotEmpty()

    fun matches(tx: Transaction, ref: RefData, c: Classifier): Boolean {
        val catOk = !hasCategoryPart || categoryMatch(tx, ref, c)
        val accOk =
            accounts.isEmpty() || tx.accountId in accounts ||
                (tx.kind == TxKind.TRANSFER_OUT && tx.counterpartAccountId in accounts)
        return catOk && accOk
    }

    private fun categoryMatch(tx: Transaction, ref: RefData, c: Classifier): Boolean {
        val flow = c.classify(tx.kind, tx.accountId, tx.counterpartAccountId)
        if (incomeAll && flow == CashFlow.INCOME) return true
        if (expenseAll && flow == CashFlow.EXPENSE) return true
        when (tx.kind) {
            TxKind.INCOME, TxKind.EXPENSE -> {
                val income = tx.kind == TxKind.INCOME
                if (tx.isBalanceAdjustment) return if (income) modifiedIncome else modifiedExpense
                val main =
                    ref.mainCategoryId(tx.categoryId)
                        ?: return if (income) noneIncome else noneExpense
                val sub = ref.subCategoryId(tx.categoryId)
                if (main in categories) return true
                if (sub != null && sub in categories) return true
                if (sub == null && main in otherOf) return true
            }

            TxKind.TRANSFER_OUT -> {
                if (flow == CashFlow.EXPENSE && tx.counterpartAccountId in teExpense) return true
                if (flow == CashFlow.INCOME && tx.accountId in teIncome) return true
            }

            else -> Unit
        }
        return false
    }
}
