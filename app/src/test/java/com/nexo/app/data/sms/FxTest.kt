package com.nexo.app.data.sms

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.RateSide
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsReason
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.database.Seeder
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.data.database.entity.FxSnapshotEntity
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.datastore.DevicePrefs
import com.nexo.app.data.network.BankRatesClient
import com.nexo.app.data.repository.FxRepository
import com.nexo.app.data.repository.RefRepository
import com.nexo.app.data.sms.RateChoice
import com.nexo.app.data.sms.RateChooser
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bank-rate parsing, rate choice and estimates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FxTest {
    private val sample = """
        { "country": "lk", "quote_ccy": "LKR", "official_date": "2026-11-13",
          "banks": [
            { "bank": "combank", "name": "Commercial Bank of Ceylon", "bank_updated_at": "13 Nov 2026 - 11:30AM",
              "basis": "Telegraphic transfer", "captured_at": "2026-11-14T02:37:43.895Z",
              "rates": [ { "ccy": "USD", "buy": 328.5, "sell": 336.5, "mid": 332.5, "spread_pct": 2.406, "official": 332.5018 },
                         { "ccy": "UAE", "buy": 88.0, "sell": 92.0 },
                         { "ccy": "JPY", "buy": 2.1, "sell": 2.2 } ] },
            { "bank": "hnb", "bank_updated_at": null, "captured_at": "2026-11-14T02:30:00Z",
              "rates": [ { "ccy": "USD", "buy": 329.0, "sell": 337.0, "official": null } ] },
            { "bank": "boc", "captured_at": "2026-11-14T02:31:00Z",
              "rates": [ { "ccy": "USD", "buy": 328.0, "sell": 336.0, "spread_pct": 2.5, "official": 332.5 },
                         { "ccy": "EUR", "sell": 391.2 } ] }
          ] }
    """.trimIndent()
    private val captured = Instant.parse("2026-11-14T02:37:43.895Z").toEpochMilli()

    @Test
    fun parseKeepsKnownIsoCurrenciesAndCapturedAt() {
        val rows = BankRatesClient.parse(sample)
        assertEquals(4, rows.size)
        val combank = rows.single { it.bank == "combank" }
        assertEquals("USD", combank.ccy)
        assertEquals(336.5, combank.sell!!, 0.0)
        assertEquals(captured, combank.capturedAt)
        assertNull(rows.single { it.bank == "hnb" }.official)
        assertTrue(BankRatesClient.parse("not json").isEmpty())
        assertTrue(BankRatesClient.parse("""{"error":"Commercial bank rates need an API key."}""").isEmpty())
    }

    @Test
    fun issuerRateFirstThenMedianThenOfficial() {
        val usd = BankRatesClient.parse(sample).filter { it.ccy == "USD" }
        assertEquals(RateChoice(336.5, "combank", captured), RateChooser.choose(usd, "combank", RateSide.SELL))
        assertEquals(328.5, RateChooser.choose(usd, "combank", RateSide.BUY)!!.rate, 0.0)
        // NTB isn't on this board: the median of 336.0, 336.5 and 337.0.
        val median = RateChooser.choose(usd, "ntb", RateSide.SELL)!!
        assertEquals(RateChoice.MEDIAN, median.source)
        assertEquals(336.5, median.rate, 0.0)
        // Only official rates: CBSL plus half the median spread.
        val officialOnly = usd.map { it.copy(buy = null, sell = null) }
        val official = RateChooser.choose(officialOnly, "combank", RateSide.SELL)!!
        assertEquals(RateChoice.OFFICIAL, official.source)
        assertEquals(332.5009 * (1 + 2.453 / 2 / 100), official.rate, 0.01)
        assertNull(RateChooser.choose(emptyList(), "combank", RateSide.SELL))
    }

    @Test
    fun estimatesRoundHalfUpInDecimal() {
        assertEquals(6730.0, RateChooser.estimate(20.0, 336.5), 0.0)
        assertEquals(5384.0, RateChooser.estimate(16.0, 336.5), 0.0)
        assertEquals(336.5, RateChooser.estimate(1.0, 336.5), 0.0)
        assertEquals(4.21, RateChooser.estimate(0.0125, 336.5), 0.0)
    }

    private fun repo(): Triple<NexoDatabase, FxRepository, RefRepository> = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, NexoDatabase::class.java).allowMainThreadQueries().build()
        Seeder(context, db).seedFresh(CurrencyCatalog.find("LKR")!!)
        val refs = RefRepository(db, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        Triple(db, FxRepository(db, refs, DevicePrefs(context), BankRatesClient()), refs)
    }

    private fun usdItem(occurredAt: Long, amount: Double = 20.0) = SmsItemEntity(
        id = "i", certainty = Certainty.ESTIMATED, reason = SmsReason.FX_ESTIMATE, kind = SmsKind.EXPENSE, bank = "COMBANK",
        sender = "COMBANK", template = "purchase", amount = amount, currency = "USD", occurredAt = occurredAt, smsAt = occurredAt,
        capturedAt = occurredAt, dedupeKey = "k", text = "", updatedAt = occurredAt,
    )

    @Test
    fun estimateUsesTheBoardOnOrBeforeTheTransactionDay() = runBlocking {
        val (db, fx) = repo().let { it.first to it.second }
        val day = 24 * 3600 * 1000L
        fx.store(listOf(FxSnapshotEntity(captured - 3 * day, "combank", "USD", 327.0, 335.0)))
        fx.store(BankRatesClient.parse(sample))
        // Same rates a day later add nothing to the history.
        fx.store(BankRatesClient.parse(sample).map { it.copy(capturedAt = it.capturedAt + day) })
        val rows = db.query("SELECT COUNT(*) FROM fx_snapshots", null).use { it.moveToFirst(); it.getInt(0) }
        assertEquals(5, rows)

        val today = fx.estimate(usdItem(captured + 3600_000))
        assertEquals(6730.0, today.estimatedAmount!!, 0.0)
        assertEquals("combank", today.fxSource)
        assertEquals(RateSide.SELL, today.fxSide)
        assertEquals(captured, today.fxSnapshotAt)

        val twoDaysAgo = fx.estimate(usdItem(captured - 2 * day))
        assertEquals(335.0, twoDaysAgo.fxRate!!, 0.0)
        assertEquals(6700.0, twoDaysAgo.estimatedAmount!!, 0.0)

        // Older than any snapshot: the earliest one, which carries its own date.
        val longAgo = fx.estimate(usdItem(captured - 30 * day))
        assertEquals(captured - 3 * day, longAgo.fxSnapshotAt)
        db.close()
    }

    @Test
    fun withoutSnapshotsTheCurrencySettingsRateIsUsed() = runBlocking {
        val (db, fx, refs) = repo()
        assertNull(fx.estimate(usdItem(captured)).estimatedAmount)
        db.currencyDao().upsert(listOf(CurrencyEntity("USD", "USD", "US Dollar", "$", SymbolPosition.PREFIX, 2, 330.0, false, true, 1, 0)))
        val ref = refs.ref.first { r -> r.currencies.any { it.id == "USD" } }
        val manual = fx.estimate(usdItem(captured), ref)
        assertEquals(RateChoice.MANUAL, manual.fxSource)
        assertEquals(6600.0, manual.estimatedAmount!!, 0.0)
        db.close()
    }

    @Test
    fun confirmedAmountsAreNotReEstimated() = runBlocking {
        val (db, fx) = repo().let { it.first to it.second }
        fx.store(BankRatesClient.parse(sample))
        val confirmed = usdItem(captured).copy(certainty = Certainty.EXACT, actualAmount = 6900.0)
        assertEquals(confirmed, fx.estimate(confirmed))
        db.close()
    }
}
