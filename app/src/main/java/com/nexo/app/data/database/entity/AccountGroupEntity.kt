package com.nexo.app.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType

@Entity(tableName = "account_groups")
data class AccountGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: GroupType,
    /** 1..11 for the seeded groups (Cash = 11, Accounts = 1, Card = 2, Debit Card = 3, ...), null for user groups. */
    val builtinId: Int?,
    val orderSeq: Int,
    val status: GroupStatus = GroupStatus.ACTIVE,
    val updatedAt: Long,
)
