package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Key/value book settings; they travel with backups.
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)
