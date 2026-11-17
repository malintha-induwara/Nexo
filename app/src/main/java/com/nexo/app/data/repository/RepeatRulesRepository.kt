package com.nexo.app.data.repository

import com.nexo.app.core.model.RepeatRule
import kotlinx.coroutines.flow.Flow

interface RepeatRulesRepository {
    fun getRulesStream(): Flow<List<RepeatRule>>

    suspend fun getRule(id: String): RepeatRule?

    // Also generates whatever is now due.
    suspend fun save(rule: RepeatRule)

    suspend fun delete(ids: List<String>)

    // At most once per calendar day unless [force] is set.
    suspend fun generateIfNewDay(force: Boolean = false)

    // Generates every transaction due by today; returns how many were created.
    suspend fun generate(): Int
}
