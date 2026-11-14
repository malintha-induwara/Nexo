package com.nexo.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexo.app.R
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.domain.CardFigures
import com.nexo.app.ui.components.AmountText
import com.nexo.app.ui.components.IconTile
import com.nexo.app.ui.components.RollingAmount
import com.nexo.app.ui.components.pressScale
import com.nexo.app.ui.components.shrinkToFit
import com.nexo.app.ui.shell.LocalShell
import com.nexo.app.ui.theme.CategoryHues
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoShapes
import com.nexo.app.ui.theme.NexoType
import com.nexo.app.ui.theme.Space
import java.util.Locale
import kotlin.math.abs

private val Balance = NexoType.AmountLarge.copy(fontSize = 24.sp, lineHeight = 30.sp)
private val SplitAmount = NexoType.AmountLarge.copy(fontSize = 19.sp, lineHeight = 24.sp)

@Composable
private fun AccountUi.hue(): Color = CategoryHues.of(visual.hue, LocalNexoColors.current.isDark)

/** Balance colour on a neutral ground: red when owed, dim when left out of totals, plain otherwise. */
@Composable
private fun AccountUi.balanceColor(): Color {
    val colors = LocalNexoColors.current
    return when {
        muted -> colors.textTertiary
        neutral -> colors.text
        balance < 0 -> LocalMoneyColors.current.expense
        else -> colors.text
    }
}

/** Payable and outstanding side by side, each under its label; overdue beneath when there is any. */
@Composable
private fun CardSplit(card: CardFigures, currency: CurrencyEntity, label: Color, value: (Double) -> Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.payable).uppercase(Locale.getDefault()), style = NexoType.Caps, color = label, maxLines = 1)
                AmountText(abs(card.payable), currency, Modifier.shrinkToFit(), style = SplitAmount, color = value(card.payable))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.outstanding).uppercase(Locale.getDefault()), style = NexoType.Caps, color = label, maxLines = 1)
                AmountText(abs(card.outstanding), currency, Modifier.shrinkToFit(), style = SplitAmount, color = value(card.outstanding))
            }
        }
        if (card.other < 0) {
            Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.overdue), style = NexoType.Meta, color = label)
                Spacer(Modifier.width(Space.xs))
                AmountText(abs(card.other), currency, style = NexoType.AmountSmall, color = value(card.other))
            }
        }
    }
}

/** "↑ Rs 5,000.00" against the previous period. */
@Composable
private fun ChangeCaption(change: Double, currency: CurrencyEntity, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (change != 0.0) {
            Icon(if (change > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(Space.xxs))
        }
        AmountText(abs(change), currency, style = NexoType.AmountSmall, color = color)
    }
}

/** Net worth as the screen's one large number, with assets and liabilities beneath. */
@Composable
private fun NetWorthHero(ui: AccountsUi, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    Column(modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.m)) {
        Text(stringResource(R.string.net_worth), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        RollingAmount(ui.summary.total, ui.main, Modifier.shrinkToFit(), color = colors.text)
        Spacer(Modifier.height(Space.s))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            HeroFigure(stringResource(R.string.assets), ui.summary.assets, ui.main, money.income, colors.textTertiary)
            HeroFigure(stringResource(R.string.liabilities), ui.summary.liabilities, ui.main, money.expense, colors.textTertiary)
        }
    }
}

@Composable
private fun HeroFigure(label: String, value: Double, currency: CurrencyEntity, color: Color, labelColor: Color) {
    Column {
        Text(label.uppercase(Locale.getDefault()), style = NexoType.Caps, color = labelColor)
        Spacer(Modifier.height(Space.xxs))
        RollingAmount(value, currency, style = NexoType.Amount, color = color, minorScale = 0.8f)
    }
}

/** Group name over its cards, with the group's total; tap to fold the group. */
@Composable
private fun SectionLabel(section: AccountSection, main: CurrencyEntity, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val total = when {
        section.neutral || section.total >= 0 -> colors.textSecondary
        else -> LocalMoneyColors.current.expense
    }
    Row(
        modifier
            .fillMaxWidth()
            .then(if (section.collapsible) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onToggle(section.id) } else Modifier)
            .padding(start = Space.gutter, end = Space.l, top = Space.gutter, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.title.uppercase(Locale.getDefault()), style = NexoType.Caps, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        AmountText(section.total, main, style = NexoType.AmountSmall, color = total)
        if (section.collapsible) {
            Icon(if (section.collapsed) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowUp, null, tint = colors.textTertiary, modifier = Modifier.padding(start = Space.xs).size(18.dp))
        }
    }
}

/** One neutral full-width card per account, grouped, with the balance set large under the name. */
@Composable
internal fun AccountCards(ui: AccountsUi, onToggle: (String) -> Unit, onOpen: (AccountUi) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalShell.current.bottomInset + Space.m)) {
        item(key = "hero") { NetWorthHero(ui) }
        ui.sections.forEach { s ->
            item(key = "g_${s.id}") { SectionLabel(s, ui.main, onToggle, Modifier.animateItem()) }
            if (!s.collapsed) {
                items(s.accounts, key = { it.key }) { a ->
                    AccountCard(a, { onOpen(a) }, Modifier.animateItem().padding(horizontal = Space.l, vertical = Space.xs))
                }
            }
        }
    }
}

@Composable
private fun AccountCard(a: AccountUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interactions, 0.98f)
            .fillMaxWidth()
            .clip(NexoShapes.panel)
            .background(colors.surface)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = Space.l, top = 14.dp, bottom = 14.dp, end = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(a.visual.icon, a.hue(), size = 48.dp, over = colors.surface)
        Spacer(Modifier.width(Space.l))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, style = MaterialTheme.typography.bodyMedium, color = if (a.muted) colors.textTertiary else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.muted) {
                    Text(" · " + stringResource(R.string.not_in_totals), style = NexoType.Meta, color = colors.textTertiary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(Space.xxs))
            val card = a.card
            if (card != null) {
                CardSplit(card, a.currency, colors.textTertiary, { money.signed(it) }, Modifier.padding(top = Space.xs))
            } else {
                AmountText(a.balance, a.currency, Modifier.shrinkToFit(), style = Balance, color = a.balanceColor())
            }
            a.change?.let { ChangeCaption(it, a.currency, colors.textSecondary, Modifier.padding(top = Space.xxs)) }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp))
    }
}
