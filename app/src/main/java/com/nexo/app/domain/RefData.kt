package com.nexo.app.domain

import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.AccountGroupEntity
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.database.entity.TagEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.settings.BookSettings

/** Snapshot of the reference tables and book settings, used to render and classify transactions. */
data class RefData(
    val groups: List<AccountGroupEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val currencies: List<CurrencyEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val settings: BookSettings = BookSettings(),
) {
    val groupById: Map<String, AccountGroupEntity> = groups.associateBy { it.id }
    val accountById: Map<String, AccountEntity> = accounts.associateBy { it.id }
    val categoryById: Map<String, CategoryEntity> = categories.associateBy { it.id }
    val currencyById: Map<String, CurrencyEntity> = currencies.associateBy { it.id }
    val tagById: Map<String, TagEntity> = tags.associateBy { it.id }

    /** Resolved look of every category (emoji or monogram, hue), by id. */
    val visualById: Map<String, CategoryVisual> by lazy {
        categories.associate { c -> c.id to CategoryVisuals.resolve(c, c.parentId?.let { categoryById[it] }) }
    }

    fun visual(categoryId: String?): CategoryVisual? = categoryId?.let { visualById[it] }

    val mainCurrency: CurrencyEntity = currencies.firstOrNull { it.isMain } ?: FALLBACK_MAIN

    /** Main first, then visible sub-currencies in order. */
    val activeCurrencies: List<CurrencyEntity> =
        listOf(mainCurrency) + currencies.filter { !it.isMain && it.isVisible }.sortedBy { it.orderSeq }

    fun currency(id: String?): CurrencyEntity = id?.let { currencyById[it] } ?: mainCurrency

    fun groupOf(accountId: String?): AccountGroupEntity? = accountId?.let { accountById[it] }?.let { groupById[it.groupId] }

    fun groupType(accountId: String?): GroupType? = groupOf(accountId)?.type

    fun isCard(accountId: String?): Boolean = groupType(accountId) == GroupType.CREDIT_CARD

    fun isDebitCard(accountId: String?): Boolean = groupType(accountId) == GroupType.DEBIT_CARD

    fun isFlagged(accountId: String?): Boolean = accountId?.let { accountById[it] }?.transferExpense == true

    fun accountCurrency(accountId: String?): CurrencyEntity = currency(accountId?.let { accountById[it] }?.currencyId)

    /** Debit cards drawing on [accountId]. */
    fun linkedDebitCards(accountId: String): List<AccountEntity> =
        accounts.filter { it.linkedAccountId == accountId && groupType(it.id) == GroupType.DEBIT_CARD }

    fun isAccountVisible(a: AccountEntity): Boolean {
        val g = groupById[a.groupId]
        return a.status == AccountStatus.ACTIVE && g != null && g.status == GroupStatus.ACTIVE
    }

    /** Accounts offered in pickers: active, in active groups, ordered by group then account order. */
    val pickerAccounts: List<AccountEntity> by lazy { orderedAccounts().filter { isAccountVisible(it) } }

    fun orderedAccounts(): List<AccountEntity> {
        val groupOrder = groups.withIndex().associate { it.value.id to it.index }
        return accounts.sortedWith(
            compareBy<AccountEntity>({ groupOrder[it.groupId] ?: Int.MAX_VALUE }, { groupById[it.groupId]?.name ?: "" }, { it.orderSeq }, { it.createdAt }),
        )
    }

    fun mainCategories(kind: CategoryKind): List<CategoryEntity> =
        categories.filter { it.kind == kind && it.parentId == null && !it.isDeleted }

    fun subCategories(parentId: String): List<CategoryEntity> =
        categories.filter { it.parentId == parentId && !it.isDeleted }

    /** Main category id of a stored category id (DM-103). */
    fun mainCategoryId(categoryId: String?): String? {
        val c = categoryId?.let { categoryById[it] } ?: return null
        return c.parentId?.takeIf { categoryById.containsKey(it) } ?: c.id
    }

    fun subCategoryId(categoryId: String?): String? {
        val c = categoryId?.let { categoryById[it] } ?: return null
        return if (c.parentId != null && categoryById.containsKey(c.parentId)) c.id else null
    }

    fun categoryName(id: String?): String? = id?.let { categoryById[it]?.name }

    companion object {
        val FALLBACK_MAIN = CurrencyEntity(
            id = "USD", isoCode = "USD", name = "USD", symbol = "$", symbolPosition = SymbolPosition.PREFIX,
            decimals = 2, rate = 1.0, isMain = true, isVisible = true, orderSeq = 0, updatedAt = 0,
        )
    }
}
