package com.nexo.app.data.repository

import com.nexo.app.data.database.entity.RepeatRuleEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Repeat rules and the transactions they generate. */
interface RepeatRulesRepository {
    fun getRulesStream(): Flow<List<RepeatRuleEntity>>

    suspend fun getRule(id: String): RepeatRuleEntity?

    /** Saves the rule and generates whatever is now due. */
    suspend fun save(rule: RepeatRuleEntity)

    suspend fun delete(ids: List<String>)

    /** Generates due transactions at most once per calendar day unless [force] is set. */
    suspend fun generateIfNewDay(force: Boolean = false)

    /** Generates every transaction due by [today]; returns how many were created. */
    suspend fun generate(today: LocalDate = LocalDate.now()): Int
}
