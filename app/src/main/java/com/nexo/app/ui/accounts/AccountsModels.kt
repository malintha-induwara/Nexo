package com.nexo.app.ui.accounts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.nexo.app.core.database.entity.AccountGroupEntity
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.core.model.GroupType
import com.nexo.app.domain.AccountsSummary
import com.nexo.app.domain.CardFigures
import com.nexo.app.domain.CategoryVisuals

/** How an account group looks: an icon and one of the ten category hues. */
data class GroupVisual(val icon: ImageVector, val hue: Int)

object GroupVisuals {
    val None = GroupVisual(Icons.Rounded.Inbox, 9)

    /** Seeded groups get a fixed look; the user's own groups take a stable hue from their id. */
    fun of(group: AccountGroupEntity): GroupVisual = when (group.builtinId) {
        11 -> GroupVisual(Icons.Rounded.Payments, 3)
        1 -> GroupVisual(Icons.Rounded.AccountBalance, 5)
        2 -> GroupVisual(Icons.Rounded.CreditCard, 7)
        3 -> GroupVisual(Icons.Rounded.CreditCard, 6)
        4 -> GroupVisual(Icons.Rounded.Savings, 2)
        6 -> GroupVisual(Icons.Rounded.PhoneAndroid, 1)
        8 -> GroupVisual(Icons.Rounded.TrendingUp, 4)
        9 -> GroupVisual(Icons.Rounded.SwapVert, 8)
        5 -> GroupVisual(Icons.Rounded.RequestQuote, 0)
        10 -> GroupVisual(Icons.Rounded.Shield, 6)
        7 -> GroupVisual(Icons.Rounded.Category, 9)
        else -> GroupVisual(
            if (group.type == GroupType.NORMAL) Icons.Rounded.AccountBalanceWallet else Icons.Rounded.CreditCard,
            CategoryVisuals.stableColor(group.id),
        )
    }
}

/** One account card. Amounts are in [currency]. */
data class AccountUi(
    val key: String,
    /** Null for the "None" pseudo-account. */
    val accountId: String?,
    val name: String,
    val groupName: String,
    val visual: GroupVisual,
    val currency: CurrencyEntity,
    val balance: Double,
    /** Statement split, shown instead of [balance] when present. */
    val card: CardFigures? = null,
    /** Left out of the totals: drawn dimmed. */
    val muted: Boolean = false,
    /** Debit cards show spending, not a balance: never coloured by sign. */
    val neutral: Boolean = false,
    /** Change against the previous period, in period modes. */
    val change: Double? = null,
)

/** A group of account cards. [total] is in the main currency. */
data class AccountSection(
    val id: String,
    val title: String,
    val visual: GroupVisual,
    val total: Double,
    val neutral: Boolean,
    val collapsed: Boolean,
    val collapsible: Boolean,
    val accounts: List<AccountUi>,
)

data class AccountsUi(
    val sections: List<AccountSection>,
    val summary: AccountsSummary,
    val main: CurrencyEntity,
)
