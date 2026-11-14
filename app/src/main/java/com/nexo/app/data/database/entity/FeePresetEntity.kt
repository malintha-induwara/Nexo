package com.nexo.app.data.database.entity

import androidx.room.Entity
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeeRoute

/** Fee preset, keyed by source account and route. */
@Entity(tableName = "fee_presets", primaryKeys = ["accountId", "route"])
data class FeePresetEntity(
    val accountId: String,
    val route: FeeRoute,
    val lastFee: Double? = null,
    val mode: FeeMode,
    val updatedAt: Long,
)
