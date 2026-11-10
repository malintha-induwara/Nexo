package com.nexo.app.domain

import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.money.Money

data class Amounts(val amount: Double, val main: Double, val account: Double)

/** Three-amount derivation (DM-100, TX-022). */
object Conversion {
    fun effectiveRate(c: CurrencyEntity): Double = if (c.rate == 0.0) 1.0 else c.rate

    /**
     * [rate] = main units per one unit of [tx] (the form's rate, defaulting to the stored rate).
     * [crossRate], when given, is the original account/transaction ratio preserved on edit.
     */
    fun compute(
        typed: Double,
        tx: CurrencyEntity,
        account: CurrencyEntity,
        main: CurrencyEntity,
        rate: Double = effectiveRate(tx),
        crossRate: Double? = null,
    ): Amounts {
        val amount = Money.round(typed, tx.decimals)
        val mainAmount = if (tx.id == main.id) amount else Money.round(Money.multiply(amount, rate), main.decimals)
        val acct = when {
            crossRate != null -> Money.round(Money.multiply(amount, crossRate), account.decimals)
            tx.id == account.id -> amount
            account.id == main.id -> mainAmount
            else -> Money.round(Money.divide(mainAmount, effectiveRate(account)), account.decimals)
        }
        return Amounts(amount, mainAmount, acct)
    }

    /** Account amount for the other side of a transfer (DM-021 leg B). */
    fun accountAmount(amount: Double, mainAmount: Double, tx: CurrencyEntity, account: CurrencyEntity, main: CurrencyEntity): Double = when {
        tx.id == account.id -> amount
        account.id == main.id -> mainAmount
        else -> Money.round(Money.divide(mainAmount, effectiveRate(account)), account.decimals)
    }
}
