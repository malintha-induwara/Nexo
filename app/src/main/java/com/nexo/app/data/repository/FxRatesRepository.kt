package com.nexo.app.data.repository

import com.nexo.app.core.model.FxSnapshot
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.data.network.FxRatesNetworkDataSource
import com.nexo.app.data.sms.parser.BodyDate
import java.time.Instant
import java.time.LocalDate

/**
 * Exchange-rate snapshots and the LKR estimates of foreign-currency queue items.
 * Without an API key, or before the first fetch, Nexo's own currency rate is used where one is set.
 */
interface FxRatesRepository {
    /** Whether bank boards can be fetched at all (an API key is set). */
    val online: Boolean

    /** Fetch policy: at most every 6 hours. Returns whether a fetch succeeded. */
    suspend fun refreshIfStale(now: Long = System.currentTimeMillis()): Boolean

    /** Keeps new boards only when a bank's rates changed; older boards always fill the history. */
    suspend fun store(rows: List<FxSnapshot>)

    /** [item] with its estimate filled from the closest board, or from Nexo's own rate. */
    suspend fun estimate(item: SmsItem, ref: RefData? = null, allowNetwork: Boolean = false): SmsItem

    /** Fills "Rate pending" queue items; returns how many changed. */
    suspend fun fillPending(allowNetwork: Boolean = false): Int

    companion object {
        /** Shown wherever a bank-board rate is used. */
        const val ATTRIBUTION_URL = FxRatesNetworkDataSource.ATTRIBUTION_URL

        const val REFRESH_MS = 6 * 60 * 60 * 1000L

        /** The bank-board day of an instant (boards are dated in Sri Lanka time). */
        fun dayOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(BodyDate.ZONE).toLocalDate()
    }
}
