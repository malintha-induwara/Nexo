package com.nexo.app.core.model

data class Ledger(
    val rows: List<Transaction> = emptyList(),
    val tagsByTx: Map<String, List<String>> = emptyMap(),
    val photoTx: Set<String> = emptySet(),
)
