package com.nexo.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.GroupType
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.ModePill
import com.nexo.app.ui.designsystem.component.MonthPickerDialog
import com.nexo.app.ui.designsystem.component.NexoDatePickerDialog
import com.nexo.app.ui.designsystem.component.NexoMenu
import com.nexo.app.ui.designsystem.component.NexoMenuItem
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.AccountListMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.TotalStatsKey

@Composable
fun accountsModeLabel(m: AccountsMode): String = stringResource(
    when (m) {
        AccountsMode.ALL -> R.string.all
        AccountsMode.WEEKLY -> R.string.weekly
        AccountsMode.MONTHLY -> R.string.monthly
        AccountsMode.ANNUALLY -> R.string.annually
        AccountsMode.PERIOD -> R.string.period
    },
)

@Composable
fun AccountsTab(todaySignal: Int, vm: AccountsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ref = state.ref
    val data = state.data
    val q = state.query
    val device = state.device
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    var menu by rememberSaveable { mutableStateOf(false) }
    var modePicker by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(todaySignal) { if (todaySignal > 0) vm.goToday() }
    val main = ref.mainCurrency

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(end = Space.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (q.mode) {
                AccountsMode.ALL -> Text(
                    stringResource(R.string.tab_accounts),
                    color = colors.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Space.gutter),
                )
                AccountsMode.PERIOD -> DateRangeChips(q.from, q.to, onChange = vm::setRange, color = colors.text)
                else -> {
                    val r = data.range
                    val label = when (q.mode) {
                        AccountsMode.WEEKLY -> r?.let { Formats.range(it.start, it.end) } ?: ""
                        AccountsMode.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(q.anchor).label)
                        else -> ref.settings.periods.yearOf(q.anchor).toString()
                    }
                    PeriodNavigator(
                        label, { vm.step(false) }, { vm.step(true) },
                        if (q.mode == AccountsMode.ANNUALLY) null else ({ datePicker = true }),
                        modifier = Modifier.padding(start = Space.xs),
                        color = colors.text,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            ModePill(accountsModeLabel(q.mode)) { haptics.tick(); modePicker = true }
            Spacer(Modifier.width(Space.xs))
            RoundIconButton(Icons.Rounded.BarChart, stringResource(R.string.total_stats), { nav.navigate(TotalStatsKey) }, tint = colors.textSecondary, container = Color.Transparent)
            Box {
                RoundIconButton(Icons.Rounded.MoreVert, stringResource(R.string.action_more), { menu = true }, tint = colors.textSecondary, container = Color.Transparent)
                NexoMenu(menu, onDismissRequest = { menu = false }) {
                    NexoMenuItem(stringResource(R.string.action_add), { menu = false; nav.navigate(AccountFormKey()) }, Icons.Rounded.Add)
                    NexoMenuItem(stringResource(R.string.show_hide), { menu = false; nav.navigate(AccountListKey(AccountListMode.VISIBILITY)) }, Icons.Rounded.Visibility)
                    NexoMenuItem(stringResource(R.string.modify_orders), { menu = false; nav.navigate(AccountListKey(AccountListMode.REORDER)) }, Icons.Rounded.SwapVert)
                    NexoMenuItem(stringResource(R.string.action_delete), { menu = false; nav.navigate(AccountListKey(AccountListMode.DELETE)) }, Icons.Rounded.DeleteOutline, danger = true)
                }
            }
        }

        val periodMode = data.range != null
        val collapsed = device?.collapsedGroups.orEmpty()
        val noneLabel = stringResource(R.string.none)
        val sections = remember(ref, data, collapsed, noneLabel) {
            buildList {
                data.unassigned?.let { value ->
                    val account = AccountUi("a_none", null, noneLabel, noneLabel, GroupVisuals.None, main, value)
                    add(AccountSection("none", noneLabel, GroupVisuals.None, value, neutral = false, collapsed = false, collapsible = false, accounts = listOf(account)))
                }
                val figuresByGroup = data.figures.groupBy { it.account.groupId }
                ref.groups.filter { it.status == GroupStatus.ACTIVE }.forEach { g ->
                    val figures = figuresByGroup[g.id].orEmpty().filter { it.account.status == AccountStatus.ACTIVE || it.account.status == AccountStatus.HIDDEN }
                    if (figures.isEmpty()) return@forEach
                    val visual = GroupVisuals.of(g)
                    val debit = g.type == GroupType.DEBIT_CARD
                    val title = if (debit && data.debitMonthLabel != null) "${g.name} (${Formats.monthShort(data.debitMonthLabel!!.monthValue)})" else g.name
                    val gf = data.summary.groups[g.id]
                    // A card group owes what is payable now plus what is still building up.
                    val total = if (g.type == GroupType.CREDIT_CARD && !periodMode) Money.add(gf?.subtotal ?: 0.0, gf?.payableSubtotal ?: 0.0) else gf?.subtotal ?: 0.0
                    val accounts = figures.filter { it.account.status == AccountStatus.ACTIVE }.map { f ->
                        val spending = f.groupType == GroupType.DEBIT_CARD && !periodMode
                        AccountUi(
                            key = "a_${f.account.id}",
                            accountId = f.account.id,
                            name = f.account.name,
                            groupName = g.name,
                            visual = visual,
                            currency = ref.accountCurrency(f.account.id),
                            balance = f.balance,
                            card = f.card.takeIf { !periodMode },
                            muted = f.excluded && !spending,
                            neutral = spending,
                            change = data.changes[f.account.id].takeIf { periodMode },
                        )
                    }
                    add(AccountSection(g.id, title, visual, total, neutral = debit, collapsed = g.id in collapsed, collapsible = true, accounts = accounts))
                }
            }
        }
        if (data.loaded && data.figures.none { it.account.status == AccountStatus.ACTIVE } && data.unassigned == null) {
            EmptyState()
            return@Column
        }
        val ui = AccountsUi(sections, data.summary, main)
        val onToggle: (String) -> Unit = { haptics.tick(); vm.toggleCollapsed(it) }
        val onOpen: (AccountUi) -> Unit = { a -> nav.navigate(AccountDetailKey(a.accountId, date = if (a.accountId == null) null else data.range?.end?.toString())) }
        AccountCards(ui, onToggle, onOpen)
    }
    if (modePicker) {
        ChoiceDialog(stringResource(R.string.period), AccountsMode.entries, q.mode, { accountsModeLabel(it) }, onSelect = vm::setMode, onDismiss = { modePicker = false })
    }
    if (datePicker) {
        if (q.mode == AccountsMode.MONTHLY) {
            MonthPickerDialog(ref.settings.periods.monthPeriodContaining(q.anchor).label, onPick = { vm.setAnchor(ref.settings.periods.monthPeriodFor(it).end) }, onDismiss = { datePicker = false })
        } else {
            NexoDatePickerDialog(q.anchor, onPick = vm::setAnchor, onDismiss = { datePicker = false })
        }
    }
}
