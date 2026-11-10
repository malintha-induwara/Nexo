package com.nexo.app.domain

import android.net.Uri
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.TxKind
import java.time.LocalDateTime

sealed interface PhotoItem {
    data class Stored(val fileName: String, val checksum: String, val size: Long) : PhotoItem
    data class Picked(val uri: Uri) : PhotoItem
}

data class FeeDraft(val amount: Double, val currencyId: String, val rate: Double? = null)

/** Everything the entry form saves (TX-022). */
data class TxDraft(
    /** Row being edited. For transfers this is the outgoing leg. */
    val editingId: String? = null,
    val type: EntryType,
    /** Keeps a balance row (7/8) as such when it is edited without switching type. */
    val balanceKind: TxKind? = null,
    val isBalanceAdjustment: Boolean = false,
    val accountId: String?,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val dateTime: LocalDateTime,
    val amount: Double,
    val currencyId: String,
    /** Main-currency units per one transaction-currency unit; null = the stored rate. */
    val rate: Double? = null,
    val note: String = "",
    val description: String = "",
    val tagIds: List<String> = emptyList(),
    val photos: List<PhotoItem> = emptyList(),
    val fee: FeeDraft? = null,
    val installments: Int = 0,
    val sourceText: String? = null,
)
