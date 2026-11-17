package com.nexo.app.core.model

import java.time.LocalDateTime

enum class DateOrder(val pattern: String) {
    YMD("yyyy-MM-dd"),
    YDM("yyyy-dd-MM"),
    MDY("MM-dd-yyyy"),
    DMY("dd-MM-yyyy"),
    MYD("MM-yyyy-dd"),
    DYM("dd-yyyy-MM"),
}

enum class ImportErrorType {

    DATE,

    TYPE,

    AMOUNT,

    INCOME_CATEGORY,

    EXPENSE_CATEGORY,

    INCOME_SUB,

    EXPENSE_SUB,

    ACCOUNT,
}

data class ImportError(
    val type: ImportErrorType,
    val name: String? = null,
    val parent: String? = null,
)

data class ParsedRow(
    val dateTime: LocalDateTime?,
    val kind: TxKind?,
    val accountId: String?,
    val counterpartId: String?,
    val categoryId: String?,
    val note: String,
    val description: String,
    val main: Double,
    val amount: Double,
    val currencyId: String,
    val accountAmount: Double?,
    val tags: String,
)

data class ImportRow(
    val index: Int,
    val raw: String,
    val cells: List<String>,
    val error: ImportError?,
    val parsed: ParsedRow?,
) {
    // Listed, but skipped on save.
    val skippable: Boolean get() = cells.size < 6
}

data class ImportPreview(val rows: List<ImportRow>, val wide: Boolean)
