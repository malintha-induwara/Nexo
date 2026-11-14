package com.nexo.app.data.network

import com.nexo.app.BuildConfig
import com.nexo.app.data.database.entity.FxSnapshotEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AllRatesToday Sri Lankan bank boards. The request carries nothing about the user: no amounts, no merchants.
 * Rates are LKR per one unit of the foreign currency.
 */
@Singleton
class BankRatesClient @Inject constructor() {
    private val key: String = BuildConfig.ALLRATESTODAY_API_KEY

    val hasKey: Boolean get() = key.isNotBlank()

    /** The latest boards, or the stored boards for [date] (that day or the nearest of the 5 before). Null on any failure. */
    fun fetch(date: LocalDate? = null): List<FxSnapshotEntity>? {
        if (!hasKey) return null
        val url = URL(BASE + (date?.let { "?date=$it" } ?: ""))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            parse(connection.inputStream.bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://allratestoday.com/api/v1/bank-rates/lk"
        private const val TIMEOUT_MS = 15_000

        /** Public page that the attribution links to (the API terms require a visible link). */
        const val ATTRIBUTION_URL = "https://allratestoday.com/"

        /** The currencies Nexo's parsers can read; only these are kept, so the history stays small. */
        val CURRENCIES = setOf("USD", "EUR", "GBP", "SGD", "AUD", "INR")

        /**
         * Field quirks: `captured_at` (ISO-8601) is the snapshot time, `bank_updated_at` is ignored, non-ISO codes
         * such as "UAE" are dropped, and any rate may be missing.
         */
        fun parse(text: String): List<FxSnapshotEntity> {
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
            val banks = runCatching { root.getValue("banks").jsonArray }.getOrNull() ?: return emptyList()
            return banks.flatMap { element ->
                val bank = element as? JsonObject ?: return@flatMap emptyList()
                val code = bank.text("bank")?.lowercase(Locale.ROOT) ?: return@flatMap emptyList()
                val captured = bank.text("captured_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                    ?: return@flatMap emptyList()
                val rates = runCatching { bank.getValue("rates").jsonArray }.getOrNull() ?: return@flatMap emptyList()
                rates.mapNotNull { r ->
                    val rate = r as? JsonObject ?: return@mapNotNull null
                    val ccy = rate.text("ccy")?.uppercase(Locale.ROOT)?.takeIf { isIso(it) && it in CURRENCIES } ?: return@mapNotNull null
                    FxSnapshotEntity(
                        capturedAt = captured, bank = code, ccy = ccy,
                        buy = rate.number("buy"), sell = rate.number("sell"), mid = rate.number("mid"),
                        official = rate.number("official"), spreadPct = rate.number("spread_pct"),
                    ).takeIf { it.buy != null || it.sell != null || it.official != null }
                }
            }
        }

        private fun isIso(code: String) = code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess

        private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }
    }
}
