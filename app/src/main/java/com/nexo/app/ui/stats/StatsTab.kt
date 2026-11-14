package com.nexo.app.ui.stats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.core.model.SwipeMode
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.accounts.GroupVisuals
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.DonutChart
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.HeroStat
import com.nexo.app.ui.designsystem.component.HeroSummary
import com.nexo.app.ui.designsystem.component.IconTile
import com.nexo.app.ui.designsystem.component.ModePill
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoDatePickerDialog
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.PieSlice
import com.nexo.app.ui.designsystem.component.SegmentedPill
import com.nexo.app.ui.designsystem.component.collapsingHeader
import com.nexo.app.ui.designsystem.component.pressScale
import com.nexo.app.ui.designsystem.component.rememberCollapsingHeaderState
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.StatsDetailKey
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.navigation.StatsScope
import com.nexo.app.ui.shell.LocalShell
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.math.RoundingMode
import kotlin.math.abs

@Composable
fun scopeLabel(s: StatsScope): String = stringResource(
    when (s) {
        StatsScope.WEEKLY -> R.string.weekly
        StatsScope.MONTHLY -> R.string.monthly
        StatsScope.ANNUALLY -> R.string.annually
        StatsScope.PERIOD -> R.string.period
    },
)

private fun percentText(percent: Double, decimals: Int): String = percent.toBigDecimal().setScale(decimals, RoundingMode.HALF_UP).toPlainString() + "%"

@Composable
fun StatsTab(todaySignal: Int, vm: StatsViewModel = hiltViewModel()) {
    val ref by vm.ref.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    val money = LocalMoneyColors.current
    val haptics = rememberHaptics()
    var scopePicker by rememberSaveable { mutableStateOf(false) }
    var titlePicker by rememberSaveable { mutableStateOf(false) }
    var selectedSlice by rememberSaveable { mutableIntStateOf(-1) }
    val hero = rememberCollapsingHeaderState()
    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    LaunchedEffect(income, data.range) { hero.expand() }
    val main = ref.mainCurrency
    val rows = if (income) data.income else data.expense

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(start = Space.xs, end = Space.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (q.scope == StatsScope.PERIOD) {
                DateRangeChips(q.from, q.to, onChange = { f, t -> selectedSlice = -1; vm.setRange(f, t) }, color = colors.text)
            } else {
                val label = when (q.scope) {
                    StatsScope.WEEKLY -> Formats.range(data.range.start, data.range.end)
                    StatsScope.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(q.anchor).label)
                    else -> ref.settings.periods.yearOf(q.anchor).toString()
                }
                PeriodNavigator(
                    label, { selectedSlice = -1; vm.step(false) }, { selectedSlice = -1; vm.step(true) },
                    if (q.scope == StatsScope.ANNUALLY) null else ({ titlePicker = true }),
                    color = colors.text,
                )
            }
            Spacer(Modifier.weight(1f))
            ModePill(scopeLabel(q.scope)) { haptics.tick(); scopePicker = true }
        }
        val net = data.incomeTotal - data.expenseTotal
        HeroSummary(
            stringResource(if (income) R.string.income else R.string.expenses),
            if (income) data.incomeTotal else data.expenseTotal,
            main,
            listOf(
                if (income) HeroStat(stringResource(R.string.expenses), data.expenseTotal, money.expense)
                else HeroStat(stringResource(R.string.income), data.incomeTotal, money.income),
                HeroStat(stringResource(R.string.net), net, if (net < 0) money.expense else colors.text),
            ),
            Modifier.collapsingHeader(hero),
        )
        SegmentedPill(
            options = listOf(true, false),
            selected = income,
            onSelect = { vm.setIncome(it); selectedSlice = -1 },
            label = { isIncome -> stringResource(if (isIncome) R.string.income else R.string.expenses) },
            accent = { isIncome -> if (isIncome) money.income else money.expense },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
        )
        val threshold = with(LocalDensity.current) { 90.dp.toPx() }
        val swipe = ref.settings.swipeMode
        val swipeModifier = if (swipe == SwipeMode.NONE || q.scope == StatsScope.PERIOD) Modifier else Modifier.pointerInput(swipe, income) {
            var total = 0f
            detectHorizontalDragGestures(onDragStart = { total = 0f }, onHorizontalDrag = { _, dx -> total += dx }, onDragEnd = {
                if (abs(total) > threshold) {
                    selectedSlice = -1
                    if (swipe == SwipeMode.CHANGE_DATE) vm.step(total < 0) else vm.setIncome(total > 0)
                }
            })
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().nestedScroll(hero.connection).then(swipeModifier),
            contentPadding = PaddingValues(bottom = LocalShell.current.bottomInset + Space.m),
        ) {
            // An empty period shows the empty state alone, not an empty ring as well.
            if (rows.isEmpty()) item(key = "empty") { EmptyState() }
            else item(key = "chart") {
                Box(Modifier.fillMaxWidth().padding(top = Space.s, bottom = Space.l), contentAlignment = Alignment.Center) {
                    DonutChart(
                        rows.map { PieSlice(it.name, it.percent, NexoPalette.Stats[it.colorIndex]) },
                        selected = selectedSlice.takeIf { it in rows.indices },
                        onSlice = { if (it != selectedSlice) haptics.tick(); selectedSlice = it },
                    ) {
                        // With nothing picked the ring names its largest share.
                        ShareLabel(rows.getOrNull(selectedSlice) ?: rows.first(), main)
                    }
                }
            }
            items(rows, key = { "${it.kind}_${it.key}" }) { r ->
                CategoryCard(
                    r, ref, main,
                    onClick = { nav.navigate(StatsDetailKey(r.key, r.kind, q.scope, data.range.startText, data.range.endText, r.colorIndex)) },
                    modifier = Modifier.animateItem().padding(horizontal = Space.l, vertical = 3.dp),
                )
            }
        }
    }
    if (scopePicker) ChoiceDialog(stringResource(R.string.period), StatsScope.entries, q.scope, { scopeLabel(it) }, onSelect = { selectedSlice = -1; vm.setScope(it) }, onDismiss = { scopePicker = false })
    if (titlePicker) {
        if (q.scope == StatsScope.MONTHLY) MonthPickerDialog(ref.settings.periods.monthPeriodContaining(q.anchor).label, onPick = { selectedSlice = -1; vm.setMonth(it) }, onDismiss = { titlePicker = false })
        else NexoDatePickerDialog(q.anchor, onPick = { selectedSlice = -1; vm.setAnchor(it) }, onDismiss = { titlePicker = false })
    }
}

/** The middle of the ring: one category's share, name and amount. */
@Composable
private fun ShareLabel(row: StatsRow, main: CurrencyEntity) {
    val colors = LocalNexoColors.current
    AnimatedContent(
        targetState = row,
        transitionSpec = { fadeIn(NexoMotion.enter()) togetherWith fadeOut(NexoMotion.exit()) },
        contentKey = { "${it.kind}_${it.key}" },
        label = "share",
    ) { r ->
        Column(Modifier.widthIn(max = 132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(r.name, style = NexoType.Meta, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(percentText(r.percent, 1), style = MaterialTheme.typography.displaySmall, color = colors.text, maxLines = 1, modifier = Modifier.shrinkToFit())
            AmountText(r.amount, main, Modifier.shrinkToFit(), style = NexoType.AmountSmall, color = colors.textSecondary)
        }
    }
}

/** One category: its tile in the ring's colour, its name over its share, and the total. */
@Composable
private fun CategoryCard(row: StatsRow, ref: RefData, main: CurrencyEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val color = NexoPalette.Stats[row.colorIndex]
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interactions, 0.98f)
            .fillMaxWidth()
            .clip(NexoShapes.panel)
            .background(colors.surface)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = Space.m, top = 10.dp, bottom = 10.dp, end = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val grouped = row.kind == StatsDetailKind.TRANSFER_EXPENSE_GROUP || row.kind == StatsDetailKind.TRANSFER_INCOME_GROUP
        val visual = if (grouped) null else ref.visual(row.key)
        when {
            grouped -> IconTile(ref.groupById[row.key]?.let { GroupVisuals.of(it).icon } ?: Icons.Rounded.SwapHoriz, color, over = colors.surface)
            visual != null -> CategoryTile(visual.emoji, visual.monogram, color, over = colors.surface)
            row.key == KEY_MODIFIED -> IconTile(Icons.Rounded.Tune, color, over = colors.surface)
            else -> IconTile(Icons.Rounded.Inbox, color, over = colors.surface)
        }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.bodyMedium, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(percentText(row.percent, 0), style = NexoType.Meta, color = colors.textSecondary, maxLines = 1)
        }
        Spacer(Modifier.width(Space.m))
        AmountText(row.amount, main, style = NexoType.Amount, color = colors.text)
    }
}
