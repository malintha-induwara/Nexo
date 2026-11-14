package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.Index

/** One bank's board rate for one currency at one fetch. Kept to build a local rate history. */
@Entity(tableName = "fx_snapshots", primaryKeys = ["capturedAt", "bank", "ccy"], indices = [Index("ccy", "capturedAt")])
data class FxSnapshotEntity(
    val capturedAt: Long,
    /** API bank code, e.g. "combank". */
    val bank: String,
    val ccy: String,
    /** LKR per one unit of [ccy]. */
    val buy: Double? = null,
    val sell: Double? = null,
    val mid: Double? = null,
    val official: Double? = null,
    val spreadPct: Double? = null,
)
