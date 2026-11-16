package com.nexo.app.data.repository

import com.nexo.app.core.model.Banks
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.FxSnapshot
import com.nexo.app.core.model.RateChoice
import com.nexo.app.core.model.RateSide
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.entity.FxSnapshotEntity
import com.nexo.app.data.database.entity.asEntity
import com.nexo.app.data.database.entity.asExternalModel
import com.nexo.app.data.network.FxRatesNetworkDataSource
import com.nexo.app.data.sms.RateChooser
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class DefaultFxRatesRepository @Inject constructor(
    private val db: NexoDatabase,
    private val refs: ReferenceDataRepository,
    private val prefs: UserPreferencesRepository,
    private val client: FxRatesNetworkDataSource,
    private val clock: Clock,
) : FxRatesRepository {
    private val dao get() = db.smsDao()

    override val online: Boolean get() = client.hasKey

    /** Fetch policy: at most every 6 hours. Returns whether a fetch succeeded. */
    override suspend fun refreshIfStale(): Boolean {
        val now = clock.millis()
        if (!online || now - prefs.getDeviceSettings().fxFetchedAt < FxRatesRepository.REFRESH_MS) {
            return false
        }
        val rows = client.fetch() ?: return false
        store(rows)
        prefs.setFxFetchedAt(now)
        return true
    }

    /**
     * New boards are kept only when a bank's rates changed since its last stored board, so the history stays small.
     * Older boards (a past day fetched with `date=`) always fill the history.
     */
    override suspend fun store(rows: List<FxSnapshot>) {
        val fresh = rows.filter { r ->
            val last = dao.latestRate(r.bank, r.ccy)
            last == null || r.capturedAt < last.capturedAt ||
                (
                    r.capturedAt > last.capturedAt &&
                        (last.buy != r.buy || last.sell != r.sell || last.official != r.official)
                    )
        }
        if (fresh.isNotEmpty()) dao.insertSnapshots(fresh.map(FxSnapshot::asEntity))
    }

    /**
     * Fills [item]'s estimate. The board is each bank's latest snapshot captured on or before the transaction's date
     * (Sri Lankan time). Older than Nexo's history: with [allowNetwork], the API's stored board for that day is fetched;
     * otherwise the earliest snapshot is used and its date shows.
     */
    override suspend fun estimate(item: SmsItem, ref: RefData?, allowNetwork: Boolean): SmsItem {
        if (item.certainty != Certainty.ESTIMATED || item.actualAmount != null) return item
        val r = ref ?: refs.getRefData()
        val side = if (item.kind.isInflow) RateSide.BUY else RateSide.SELL
        val day = Instant.ofEpochMilli(item.occurredAt).atZone(Banks.ZONE).toLocalDate()
        val endOfDay = day.plusDays(1).atStartOfDay(Banks.ZONE).toInstant().toEpochMilli() - 1
        var board =
            recent(
                dao.boardAtOrBefore(item.currency, endOfDay).map(FxSnapshotEntity::asExternalModel),
                endOfDay,
            )
        if (board.isEmpty() && allowNetwork && online) {
            client.fetch(day)?.let { store(it) }
            board =
                recent(
                    dao.boardAtOrBefore(
                        item.currency,
                        endOfDay,
                    ).map(FxSnapshotEntity::asExternalModel),
                    endOfDay,
                )
        }
        if (board.isEmpty()) {
            board = dao.earliestBoard(item.currency).map(FxSnapshotEntity::asExternalModel)
        }
        val issuer = Banks.byName(item.bank)?.rateCode
        val choice =
            RateChooser.choose(board, issuer, side) ?: manual(item.currency, r) ?: return item
        return item.copy(
            fxRate = choice.rate,
            fxSide = side,
            fxSource = choice.source,
            fxSnapshotAt = choice.snapshotAt,
            estimatedAmount = RateChooser.estimate(item.amount, choice.rate),
        )
    }

    /** Re-estimates pending foreign items that have no confirmed amount, e.g. after a fetch. Returns how many changed. */
    override suspend fun fillPending(allowNetwork: Boolean): Int {
        val ref = refs.getRefData()
        var changed = 0
        for (row in dao.pending().filter {
            it.certainty == Certainty.ESTIMATED &&
                it.actualAmount == null &&
                it.status == SmsStatus.PENDING
        }) {
            val item = row.asExternalModel()
            val updated = estimate(item, ref, allowNetwork)
            if (updated != item) {
                dao.update(updated.copy(updatedAt = clock.millis()).asEntity())
                changed++
            }
        }
        return changed
    }

    /** Last fallback before asking: the rate the user keeps in Nexo's currency settings, when LKR is the main currency. */
    private fun manual(currency: String, ref: RefData): RateChoice? {
        if (!ref.mainCurrency.isoCode.equals(LKR, ignoreCase = true)) return null
        val c =
            ref.currencies.firstOrNull { it.isoCode.equals(currency, ignoreCase = true) }
                ?: return null
        return c.rate.takeIf {
            it > 0 && !c.isMain
        }?.let { RateChoice(it, RateChoice.MANUAL, null) }
    }

    /** Drops banks whose latest board is much older than the transaction (a bank that stopped publishing). */
    private fun recent(board: List<FxSnapshot>, at: Long): List<FxSnapshot> {
        val fresh = board.filter { at - it.capturedAt <= STALE_MS }
        return fresh.ifEmpty { board }
    }

    private companion object {
        const val STALE_MS = 10 * 24 * 60 * 60 * 1000L
        const val LKR = "LKR"
    }
}
