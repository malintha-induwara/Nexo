package com.nexo.app.core.model

data class AccountGroup(
    val id: String,
    val name: String,
    val type: GroupType,
    // 1..11 for seeded groups (Accounts = 1, Card = 2, …, Cash = 11); null for user groups.
    val builtinId: Int?,
    val orderSeq: Int,
    val status: GroupStatus = GroupStatus.ACTIVE,
    val updatedAt: Long,
)
