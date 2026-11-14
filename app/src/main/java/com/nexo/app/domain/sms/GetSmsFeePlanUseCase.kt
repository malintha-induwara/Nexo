package com.nexo.app.domain.sms

import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.FeeMode
import com.nexo.app.core.model.FeeRoute
import com.nexo.app.core.model.SmsItem
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.SmsRepository
import javax.inject.Inject

/** Whether approving a queue item should add, ask about, or skip a bank fee. */
class GetSmsFeePlanUseCase @Inject constructor(
    private val sms: SmsRepository,
    private val refs: ReferenceDataRepository,
) {
    suspend operator fun invoke(item: SmsItem): FeePlan {
        // The bank said what it charged: nothing to ask.
        item.statedFee?.takeIf { it > 0 }?.let { return FeePlan.Auto(item.feeRoute, it) }
        val route = item.feeRoute ?: return FeePlan.None
        val account = item.accountId ?: return FeePlan.None
        val preset = sms.getFeePreset(account, route)
        val mode = preset?.mode ?: route.defaultMode
        if (route == FeeRoute.FX_CARD_PURCHASE) {
            // Only when the real amount came out more than 0.5 % above the estimate.
            val estimate = item.estimatedAmount ?: return FeePlan.None
            val actual = item.actualAmount ?: return FeePlan.None
            if (actual <= estimate * (1 + FX_TOLERANCE)) return FeePlan.None
            val difference = Money.round(actual - estimate, refs.getRefData().accountCurrency(account).decimals)
            return when (mode) {
                FeeMode.NEVER -> FeePlan.None
                FeeMode.AUTO -> FeePlan.Auto(route, difference)
                FeeMode.ASK -> FeePlan.Ask(route, difference, fxDifference = true)
            }
        }
        val last = preset?.lastFee?.takeIf { it > 0 }
        return when {
            mode == FeeMode.NEVER -> FeePlan.None
            mode == FeeMode.AUTO && last != null -> FeePlan.Auto(route, last)
            else -> FeePlan.Ask(route, last)
        }
    }

    private companion object {
        /** An actual amount within 0.5 % of the estimate is just rounding. */
        const val FX_TOLERANCE = 0.005
    }
}
