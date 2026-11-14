package com.nexo.app.data.repository

import com.nexo.app.core.model.RepeatRule
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Repeat rules and the transactions they generate. */
interface RepeatRulesRepository {
    fun getRulesStream(): Flow<List<RepeatRule>>

    suspend fun getRule(id: String): RepeatRule?

    /** Saves the rule and generates whatever is now due. */
    suspend fun save(rule: RepeatRule)

    suspend fun delete(ids: List<String>)

    /** Generates due transactions at most once per calendar day unless [force] is set. */
    suspend fun generateIfNewDay(force: Boolean = false)

    /** Generates every transaction due by [today]; returns how many were created. */
    suspend fun generate(): Int
}
