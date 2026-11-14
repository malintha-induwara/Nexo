package com.nexo.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.period.DateRange
import com.nexo.app.core.common.period.MonthPeriod
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.RepeatRuleEntity
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import java.time.DayOfWeek
import java.time.LocalDate

/** Estimated occurrences of a rule in the current month period. */
fun estimatedCount(rule: RepeatRuleEntity, period: MonthPeriod): Int {
    val next = runCatching { LocalDate.parse(rule.nextDate) }.getOrNull() ?: return 0
    val days = period.range.days()
    return when (rule.repeatType) {
        RepeatType.MONTHLY, RepeatType.END_OF_MONTH -> 1
        RepeatType.BIMONTHLY, RepeatType.QUARTERLY, RepeatType.FOUR_MONTHLY, RepeatType.SEMIANNUAL, RepeatType.ANNUAL -> {
            val step = rule.repeatType.monthStep!!.toLong()
            if (next in period.range || next.minusMonths(step) in period.range) 1 else 0
        }
        RepeatType.DAILY -> days.size
        RepeatType.WEEKDAYS -> days.count { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        RepeatType.WEEKENDS -> days.count { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY }
        RepeatType.WEEKLY, RepeatType.BIWEEKLY, RepeatType.FOUR_WEEKLY -> {
            val step = when (rule.repeatType) {
                RepeatType.WEEKLY -> 7L
                RepeatType.BIWEEKLY -> 14L
                else -> 28L
            }
            days.count { java.time.temporal.ChronoUnit.DAYS.between(next, it).mod(step) == 0L }
        }
        RepeatType.NONE -> 0
    }
}

@Composable
fun RepeatListScreen(vm: RepeatListViewModel = hiltViewModel()) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val money = LocalMoneyColors.current
    var editing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickReflect by remember { mutableStateOf(false) }
    var confirmFirstDay by remember { mutableStateOf(false) }
    var addType by remember { mutableStateOf(false) }
    val period = ref.settings.periods.monthPeriodContaining(LocalDate.now())
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.repeat_setting), onBack = { nav.back() }) {
            if (editing) {
                IconButton(onClick = { if (checked.isNotEmpty()) confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                TextButton(onClick = { editing = false; checked = emptySet() }) { Text(stringResource(R.string.action_done)) }
            } else {
                if (rules.isNotEmpty()) IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
                IconButton(onClick = { addType = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SettingRow(
                    stringResource(R.string.repeat_reflect_title),
                    stringResource(if (ref.settings.repeatReflect == RepeatReflect.ON_DATE) R.string.reflect_on_date else R.string.reflect_first_day),
                    onClick = { pickReflect = true },
                )
                HorizontalDivider()
            }
            if (rules.isEmpty()) item {
                Column(Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.repeat_help_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.repeat_help), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            listOf(TxKind.INCOME to R.string.income, TxKind.EXPENSE to R.string.expenses, TxKind.TRANSFER_OUT to R.string.transfer).forEach { (kind, label) ->
                val group = rules.filter { it.kind == kind }
                if (group.isEmpty()) return@forEach
                item(key = "h_$kind") {
                    val estimate = group.sumOf { r ->
                        val c = ref.currency(r.currencyId)
                        val main = if (c.id == ref.mainCurrency.id) r.amount else r.amount * Conversion.effectiveRate(c)
                        main * estimatedCount(r, period)
                    }
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(stringResource(label), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(AmountFormatter.format(estimate, ref.mainCurrency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                items(group, key = { it.id }) { r -> RuleRow(r, ref, editing, r.id in checked, color = when (kind) {
                    TxKind.INCOME -> money.income
                    TxKind.EXPENSE -> money.expense
                    else -> MaterialTheme.colorScheme.onSurface
                }) {
                    if (editing) checked = if (r.id in checked) checked - r.id else checked + r.id
                    else nav.navigate(EntryKey(mode = EntryMode.REPEAT_EDIT, repeatRuleId = r.id))
                } }
            }
        }
    }
    if (pickReflect) {
        ChoiceDialog(
            title = stringResource(R.string.repeat_reflect_title),
            options = RepeatReflect.entries,
            selected = ref.settings.repeatReflect,
            label = { stringResource(if (it == RepeatReflect.ON_DATE) R.string.reflect_on_date else R.string.reflect_first_day) },
            onSelect = { if (it == RepeatReflect.FIRST_OF_PERIOD) confirmFirstDay = true else vm.setReflect(it) },
            onDismiss = { pickReflect = false },
        )
    }
    if (confirmFirstDay) {
        ConfirmDialog(
            stringResource(R.string.reflect_first_day_confirm, Formats.shortDate(period.end)),
            onConfirm = { vm.setReflect(RepeatReflect.FIRST_OF_PERIOD) },
            onDismiss = { confirmFirstDay = false },
        )
    }
    if (addType) {
        ChoiceDialog(
            title = stringResource(R.string.repeat),
            options = RepeatType.pickerOrder.filter { it != RepeatType.NONE },
            selected = null,
            label = { repeatLabel(it) },
            onSelect = { t -> nav.navigate(EntryKey(mode = EntryMode.REPEAT_NEW, repeat = t)) },
            onDismiss = { addType = false },
        )
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.want_to_delete_n, checked.size), onConfirm = { vm.delete(checked.toList()); checked = emptySet(); editing = false }, onDismiss = { confirmDelete = false })
}

@Composable
private fun RuleRow(r: RepeatRuleEntity, ref: RefData, editing: Boolean, checked: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val accountText = if (r.kind.isTransfer) "${accountLabel(ref, r.accountId)} → ${accountLabel(ref, r.toAccountId)}"
    else {
        val main = ref.categoryName(ref.mainCategoryId(r.categoryId)) ?: ""
        val sub = ref.subCategoryId(r.categoryId)?.let { ref.categoryName(it) }
        "${accountLabel(ref, r.accountId)}  ${if (sub != null) "$main/$sub" else main}"
    }
    val next = runCatching { LocalDate.parse(r.nextDate) }.getOrNull()
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (editing) Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
        Column(Modifier.weight(1f)) {
            Text(r.note.ifBlank { accountText }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.note.isNotBlank()) Text(accountText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val weekday = if (r.repeatType in setOf(RepeatType.WEEKLY, RepeatType.BIWEEKLY, RepeatType.FOUR_WEEKLY) && next != null) " (${Formats.weekdayShort(next)})" else ""
            Text(
                (next?.let { Formats.shortDate(it) } ?: "") + " · " + repeatLabel(r.repeatType) + weekday +
                    (r.endDate?.let { " · " + stringResource(R.string.repeat_end) + " " + Formats.shortDate(LocalDate.parse(it)) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
            if (ref.settings.showDescription && r.description.isNotBlank()) Text(r.description.lines().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
        }
        Text(AmountFormatter.format(r.amount, ref.currency(r.currencyId)), color = color)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Suppress("unused")
private fun range(p: MonthPeriod) = DateRange(p.start, p.end)
