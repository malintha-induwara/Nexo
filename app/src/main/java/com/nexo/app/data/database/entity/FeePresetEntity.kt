package com.nexo.app.data.database.entity

import androidx.room.Entity
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeePreset
import com.nexo.app.core.model.FeeRoute

@Entity(tableName = "fee_presets", primaryKeys = ["accountId", "route"])
data class FeePresetEntity(
    val accountId: String,
    val route: FeeRoute,
    val lastFee: Double? = null,
    val mode: FeeMode,
    val updatedAt: Long,
)

fun FeePresetEntity.asExternalModel() = FeePreset(
    accountId = accountId,
    route = route,
    lastFee = lastFee,
    mode = mode,
    updatedAt = updatedAt,
)

fun FeePreset.asEntity() = FeePresetEntity(
    accountId = accountId,
    route = route,
    lastFee = lastFee,
    mode = mode,
    updatedAt = updatedAt,
)
