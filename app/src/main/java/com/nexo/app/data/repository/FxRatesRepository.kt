package com.nexo.app.data.repository

import com.nexo.app.core.model.Banks
import com.nexo.app.core.model.FxSnapshot
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import java.time.Instant
import java.time.LocalDate

// Exchange-rate snapshots and LKR estimates for foreign-currency queue items. Without
// an API key, or before the first fetch, Nexo's own currency rate is used where set.
interface FxRatesRepository {
    // Whether bank boards can be fetched at all (an API key is set).
    val online: Boolean

    // At most every 6 hours; returns whether a fetch succeeded.
    suspend fun refreshIfStale(): Boolean

    // Keeps new boards only when a bank's rates changed; older boards always fill the history.
    suspend fun store(rows: List<FxSnapshot>)

    // Fills the estimate from the closest board, or from Nexo's own rate.
    suspend fun estimate(
        item: SmsItem,
        ref: RefData? = null,
        allowNetwork: Boolean = false,
    ): SmsItem

    // Fills "Rate pending" queue items; returns how many changed.
    suspend fun fillPending(allowNetwork: Boolean = false): Int

    companion object {
        // The API terms require a visible link to this page wherever a bank-board rate is used.
        const val ATTRIBUTION_URL = "https://allratestoday.com/"

        const val REFRESH_MS = 6 * 60 * 60 * 1000L

        // Boards are dated in Sri Lanka time.
        fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(Banks.ZONE).toLocalDate()
    }
}
