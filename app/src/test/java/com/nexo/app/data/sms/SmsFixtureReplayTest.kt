package com.nexo.app.data.sms

import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.SmsMessage
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.data.sms.parser.BodyDate
import com.nexo.app.data.sms.parser.ParsedSms
import com.nexo.app.data.sms.parser.SmsParser
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Replays a bank-SMS fixture kept outside the repo against each message's `expected` block.
 * Skipped unless NEXO_SMS_FIXTURE is set:
 *
 *     NEXO_SMS_FIXTURE=/path/to/real-sms.json \
 *       ./gradlew :app:testDebugUnitTest --tests '*SmsFixtureReplayTest' --rerun
 */
class SmsFixtureReplayTest {

    private class Row(val id: Int, val sender: String, val body: String, val expected: JsonObject)

    @Test
    fun replayMatchesExpected() = runBlocking {
        val path = System.getenv("NEXO_SMS_FIXTURE")
        assumeTrue("NEXO_SMS_FIXTURE not set", !path.isNullOrBlank())
        // Unit tests run in the module directory; relative paths are taken from the project root.
        val file = File(path!!).let {
            if (it.isAbsolute) {
                it
            } else {
                File(File(System.getProperty("user.dir") ?: ".").absoluteFile.parentFile, path)
            }
        }
        assumeTrue("Fixture not found: $file", file.exists())

        val rows = Json.parseToJsonElement(
            file.readText(),
        ).jsonObject["messages"]!!.jsonArray.map { e ->
            val o = e.jsonObject
            Row(
                o["id"]!!.jsonPrimitive.int,
                o["sender"]!!.jsonPrimitive.content,
                o["body"]!!.jsonPrimitive.content,
                o["expected"]!!.jsonObject,
            )
        }
        // Stored newest first; replay oldest first.
        val replay = rows.sortedByDescending { it.id }
        val times = timestamps(replay)

        val parser = SmsParser()
        val store = MemorySmsStore()
        val correlator = SmsCorrelator(store)
        val parsed = HashMap<Int, ParsedSms>()
        val itemOf = HashMap<Int, String>()
        val cancels = HashMap<Int, Int?>()
        for (r in replay) {
            val msg = SmsMessage(r.sender, r.body, times.getValue(r.id))
            val p = parser.parse(msg)
            parsed[r.id] = p
            when (val o = correlator.accept(msg, p, r.body, msg.timestamp)) {
                is SmsCorrelator.Outcome.Created -> itemOf[r.id] = o.item.id

                is SmsCorrelator.Outcome.Cancelled -> cancels[r.id] =
                    itemOf.entries.firstOrNull { it.value == o.item.id }?.key

                is SmsCorrelator.Outcome.Merged,
                is SmsCorrelator.Outcome.Upgraded,
                is SmsCorrelator.Outcome.Duplicate,
                ->
                    println("#${r.id}: $o")

                else -> if (p is ParsedSms.Cancel) cancels[r.id] = null
            }
        }
        val cancelledBy = cancels.entries.filter { it.value != null }.associate {
            it.value!! to
                it.key
        }

        val problems = ArrayList<String>()
        val tally = sortedMapOf<String, Int>()
        for (r in rows) {
            val e = r.expected
            val p = parsed.getValue(r.id)
            val item = itemOf[r.id]?.let { store.items[it] }
            val actual = linkedMapOf<String, String?>()
            actual["result"] = when (p) {
                is ParsedSms.Movement -> "DRAFT"
                is ParsedSms.Cancel -> "CANCEL"
                else -> "IGNORE"
            }
            when (p) {
                is ParsedSms.Movement -> {
                    actual["kind"] = p.kind.name.lowercase(Locale.ROOT)
                    actual["amount"] = p.amount.toPlainString()
                    actual["currency"] = p.currency
                    actual["certainty"] = p.certainty.name.lowercase(Locale.ROOT)
                    actual["feePrompt"] =
                        p.feeRoute?.takeIf {
                            it.defaultMode != FeeMode.NEVER
                        }?.name?.lowercase(Locale.ROOT)
                    actual["cardLast4"] = p.cardLast4
                    actual["payee"] = p.payee
                    actual["ref"] = p.reference
                    actual["route"] = p.route.takeIf { p.template == "transfer_code" }
                    actual["date"] = p.bodyDate
                    actual["merchantRaw"] = item?.merchantRaw?.ifEmpty { null }
                    actual["merchant"] = item?.merchant?.ifEmpty { null }
                    actual["merchantKey"] = item?.merchantKey?.ifEmpty { null }
                    actual["merchantSource"] =
                        if (item?.merchantFromOtp == true) "otp-message" else null
                    actual["cancelledBy"] = cancelledBy[r.id]?.toString()
                }

                is ParsedSms.Cancel -> {
                    actual["kind"] = "cancel_previous"
                    actual["merchantRaw"] = p.merchant.raw
                    actual["merchant"] = p.merchant.name
                    actual["merchantKey"] = p.merchant.key
                    actual["cardLast4"] = p.cardLast4
                    actual["cancels"] = cancels[r.id]?.toString()
                }

                else -> Unit
            }
            for ((k, v) in actual) {
                val want = (e[k] as? JsonPrimitive)?.contentOrNull
                val wantNorm = if (k == "date") want?.replace(" at ", " ") else want
                if (wantNorm != v) {
                    problems +=
                        "#${r.id} ${e["template"]?.jsonPrimitive?.contentOrNull}: $k expected=$want actual=$v"
                }
            }
            val label =
                "${actual["result"]}" +
                    (p as? ParsedSms.Movement)?.let {
                        " ${it.certainty.name.lowercase(Locale.ROOT)}"
                    }.orEmpty()
            tally[label] = (tally[label] ?: 0) + 1
        }

        println("Replayed ${rows.size} messages from ${file.name}")
        tally.forEach { (k, v) -> println("  %-22s %d".format(k, v)) }
        println(
            "  fee prompts: " +
                parsed.values.filterIsInstance<ParsedSms.Movement>().mapNotNull { m ->
                    m.feeRoute?.takeIf {
                        it.defaultMode !=
                            FeeMode.NEVER
                    }
                }.groupingBy { it }.eachCount(),
        )
        println(
            "  merchant from OTP: ${store.items.values.count {
                it.merchantFromOtp
            }}, cancelled: ${store.items.values.count { it.status == SmsStatus.CANCELLED }}",
        )
        problems.forEach(::println)
        assertTrue("${problems.size} mismatches", problems.isEmpty())
    }

    // Body time where there is one; otherwise a minute before the next newer dated message.
    private fun timestamps(oldestFirst: List<Row>): Map<Int, Long> {
        val card = Regex("""(\d{2}/\d{2}/\d{2}) (\d{1,2}:\d{2} [AP]M)""")
        val iso = Regex("""(\d{4}-\d{2}-\d{2}) at (\d{2}:\d{2}:\d{2})""")
        val dated = oldestFirst.map { r ->
            card.find(r.body)?.let { BodyDate.card(it.groupValues[1], it.groupValues[2]) }
                ?: iso.find(r.body)?.let { BodyDate.iso(it.groupValues[1], it.groupValues[2]) }
        }
        val out = HashMap<Int, Long>()
        for (i in oldestFirst.indices) {
            out[oldestFirst[i].id] = dated[i]
                ?: (i + 1 until oldestFirst.size).firstNotNullOfOrNull { dated[it] }?.minus(60_000)
                ?: (i - 1 downTo 0).firstNotNullOfOrNull { dated[it] }?.plus(60_000)
                ?: 0L
        }
        return out
    }
}
