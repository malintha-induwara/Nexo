package com.nexo.app.core.model

data class RefData(
    val groups: List<AccountGroup> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val settings: BookSettings = BookSettings(),
) {
    val groupById: Map<String, AccountGroup> = groups.associateBy { it.id }
    val accountById: Map<String, Account> = accounts.associateBy { it.id }
    val categoryById: Map<String, Category> = categories.associateBy { it.id }
    val currencyById: Map<String, Currency> = currencies.associateBy { it.id }
    val tagById: Map<String, Tag> = tags.associateBy { it.id }

    val visualById: Map<String, CategoryVisual> by lazy {
        categories.associate { c ->
            c.id to
                CategoryVisuals.resolve(c, c.parentId?.let { categoryById[it] })
        }
    }

    fun visual(categoryId: String?): CategoryVisual? = categoryId?.let { visualById[it] }

    val mainCurrency: Currency = currencies.firstOrNull { it.isMain } ?: FALLBACK_MAIN

    val activeCurrencies: List<Currency> =
        listOf(mainCurrency) +
            currencies.filter { !it.isMain && it.isVisible }.sortedBy { it.orderSeq }

    fun currency(id: String?): Currency = id?.let { currencyById[it] } ?: mainCurrency

    fun groupOf(accountId: String?): AccountGroup? = accountId?.let {
        accountById[it]
    }?.let { groupById[it.groupId] }

    fun groupType(accountId: String?): GroupType? = groupOf(accountId)?.type

    fun isCard(accountId: String?): Boolean = groupType(accountId) == GroupType.CREDIT_CARD

    fun isDebitCard(accountId: String?): Boolean = groupType(accountId) == GroupType.DEBIT_CARD

    fun isFlagged(accountId: String?): Boolean =
        accountId?.let { accountById[it] }?.transferExpense == true

    fun accountCurrency(accountId: String?): Currency = currency(
        accountId?.let {
            accountById[it]
        }?.currencyId,
    )

    fun linkedDebitCards(accountId: String): List<Account> = accounts.filter {
        it.linkedAccountId == accountId &&
            groupType(it.id) == GroupType.DEBIT_CARD
    }

    fun isAccountVisible(a: Account): Boolean {
        val g = groupById[a.groupId]
        return a.status == AccountStatus.ACTIVE && g != null && g.status == GroupStatus.ACTIVE
    }

    // Ordered by group, then by account.
    val pickerAccounts: List<Account> by lazy { orderedAccounts().filter { isAccountVisible(it) } }

    fun orderedAccounts(): List<Account> {
        val groupOrder = groups.withIndex().associate { it.value.id to it.index }
        return accounts.sortedWith(
            compareBy<Account>({ groupOrder[it.groupId] ?: Int.MAX_VALUE }, {
                groupById[it.groupId]?.name
                    ?: ""
            }, { it.orderSeq }, { it.createdAt }),
        )
    }

    fun mainCategories(kind: CategoryKind): List<Category> =
        categories.filter { it.kind == kind && it.parentId == null && !it.isDeleted }

    fun subCategories(parentId: String): List<Category> =
        categories.filter { it.parentId == parentId && !it.isDeleted }

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
        val FALLBACK_MAIN = Currency(
            id = "USD",
            isoCode = "USD",
            name = "USD",
            symbol = "$",
            symbolPosition = SymbolPosition.PREFIX,
            decimals = 2, rate = 1.0, isMain = true, isVisible = true, orderSeq = 0, updatedAt = 0,
        )
    }
}
