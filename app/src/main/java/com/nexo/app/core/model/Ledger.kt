package com.nexo.app.core.model

/** Transactions of a range with their tag links and photo flags. */
data class Ledger(
    val rows: List<Transaction> = emptyList(),
    val tagsByTx: Map<String, List<String>> = emptyMap(),
    val photoTx: Set<String> = emptySet(),
)
