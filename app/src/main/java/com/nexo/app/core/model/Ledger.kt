package com.nexo.app.core.model

import com.nexo.app.data.database.entity.TransactionEntity

/** Transactions of a range with their tag links and photo flags. */
data class Ledger(
    val rows: List<TransactionEntity> = emptyList(),
    val tagsByTx: Map<String, List<String>> = emptyMap(),
    val photoTx: Set<String> = emptySet(),
)
