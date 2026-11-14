package com.nexo.app.core.model


/** What a ledger row shows in its category column. */
sealed interface CategoryLabel {
    data object Transfer : CategoryLabel

    data object ModifiedBalance : CategoryLabel

    /** [main] is null when the row has no category ("None"); [sub] is set only while subcategories are on. */
    data class Category(val main: String?, val sub: String?) : CategoryLabel
}

fun RefData.categoryLabel(tx: Transaction): CategoryLabel = when {
    tx.kind.isTransfer -> CategoryLabel.Transfer
    tx.isBalanceAdjustment || tx.kind.isBalance -> CategoryLabel.ModifiedBalance
    else -> CategoryLabel.Category(
        main = mainCategoryId(tx.categoryId)?.let { categoryName(it) },
        sub = if (settings.subcategories) subCategoryId(tx.categoryId)?.let { categoryName(it) } else null,
    )
}

/** One day's rows in the order the user chose to see them. */
fun List<Transaction>.sortedForDay(order: TimeDisplay): List<Transaction> {
    val minute = { t: Transaction -> t.dateTime / 60_000 }
    val cmp: Comparator<Transaction> = when {
        order.byTime && order.descending -> compareByDescending(minute).thenByDescending { it.updatedAt }.thenByDescending { it.createdAt }
        order.byTime -> compareBy(minute).thenBy { it.updatedAt }.thenBy { it.createdAt }
        order.descending -> compareByDescending<Transaction> { it.updatedAt }.thenByDescending { it.dateTime }.thenByDescending { it.createdAt }
        else -> compareBy<Transaction> { it.updatedAt }.thenBy { it.dateTime }.thenBy { it.createdAt }
    }
    return sortedWith(cmp)
}
