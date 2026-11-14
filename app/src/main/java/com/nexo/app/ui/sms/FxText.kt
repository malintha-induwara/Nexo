package com.nexo.app.ui.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RateSide
import com.nexo.app.data.database.entity.SmsItemEntity
import com.nexo.app.data.network.BankRatesClient
import com.nexo.app.data.repository.FxRepository
import com.nexo.app.data.sms.RateChoice
import com.nexo.app.data.sms.parser.Bank
import com.nexo.app.ui.designsystem.component.Formats

/** "Commercial Bank sell 336.50 · Nov 14". Null when the item has no rate. */
fun fxRateLine(context: Context, item: SmsItemEntity): String? {
    val rate = item.fxRate ?: return null
    val value = AmountFormatter.number(rate, 2)
    val source = when (val s = item.fxSource) {
        RateChoice.MEDIAN -> context.getString(R.string.fx_source_median)
        RateChoice.OFFICIAL -> context.getString(R.string.fx_source_official)
        RateChoice.MANUAL -> return context.getString(R.string.fx_rate_line_manual, context.getString(R.string.fx_source_manual), value)
        else -> Bank.entries.firstOrNull { it.rateCode == s }?.displayName ?: s.orEmpty()
    }
    val side = context.getString(if (item.fxSide == RateSide.BUY) R.string.fx_side_buy else R.string.fx_side_sell)
    val day = item.fxSnapshotAt?.let { Formats.dayMonth(FxRepository.dayOf(it)) }.orEmpty()
    return context.getString(R.string.fx_rate_line, source, side, value, day).trimEnd(' ', '·')
}

/** "USD 20.00 ≈ Rs 6,730.00 · Commercial Bank sell 336.50 · Nov 14". */
fun fxEstimateNote(context: Context, item: SmsItemEntity, ref: RefData): String? {
    val estimate = item.estimatedAmount ?: return null
    val original = "${item.currency} ${AmountFormatter.number(item.amount, 2)}"
    val local = AmountFormatter.format(estimate, ref.accountCurrency(item.accountId))
    return context.getString(R.string.fx_estimate_note, original, local, fxRateLine(context, item).orEmpty())
}

/** Whether a rate shown came from AllRatesToday, which then needs its visible link. */
val SmsItemEntity.rateFromApi: Boolean get() = fxSource != null && fxSource != RateChoice.MANUAL

fun openRatesAttribution(context: Context) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BankRatesClient.ATTRIBUTION_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
