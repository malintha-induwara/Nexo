package com.nexo.app.core.common.money

import com.nexo.app.core.model.Currency

data class Amounts(val amount: Double, val main: Double, val account: Double)

object Conversion {
    fun effectiveRate(c: Currency): Double = if (c.rate == 0.0) 1.0 else c.rate

    // [rate] is main units per one [tx] unit. [crossRate] keeps the original account/transaction
    // ratio on edit.
    fun compute(
        typed: Double,
        tx: Currency,
        account: Currency,
        main: Currency,
        rate: Double = effectiveRate(tx),
        crossRate: Double? = null,
    ): Amounts {
        val amount = Money.round(typed, tx.decimals)
        val mainAmount = if (tx.id == main.id) {
            amount
        } else {
            Money.round(Money.multiply(amount, rate), main.decimals)
        }
        val acct = when {
            crossRate != null -> Money.round(Money.multiply(amount, crossRate), account.decimals)
            tx.id == account.id -> amount
            account.id == main.id -> mainAmount
            else -> Money.round(Money.divide(mainAmount, effectiveRate(account)), account.decimals)
        }
        return Amounts(amount, mainAmount, acct)
    }

    // For the other side of a transfer.
    fun accountAmount(
        amount: Double,
        mainAmount: Double,
        tx: Currency,
        account: Currency,
        main: Currency,
    ): Double = when {
        tx.id == account.id -> amount
        account.id == main.id -> mainAmount
        else -> Money.round(Money.divide(mainAmount, effectiveRate(account)), account.decimals)
    }
}
