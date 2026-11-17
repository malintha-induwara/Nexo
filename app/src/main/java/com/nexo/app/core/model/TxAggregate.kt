package com.nexo.app.core.model

// Sum of amounts per (account, counterpart, kind).
data class TxAggregate(
    val accountId: String?,
    val counterpartAccountId: String?,
    val kind: TxKind,
    val isBalanceAdjustment: Boolean,
    val main: Double,
    val account: Double,
    val count: Int,
)

data class NoteUsage(val note: String, val count: Int, val lastDate: String)
