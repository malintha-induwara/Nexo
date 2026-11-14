package com.nexo.app.ui.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.components.SegmentedPill
import com.nexo.app.ui.theme.Space
import com.nexo.app.core.database.entity.TransactionEntity
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.domain.Classifier
import com.nexo.app.domain.RefData
import com.nexo.app.domain.Totals
import com.nexo.app.ui.theme.LocalMoneyColors
import com.nexo.app.ui.theme.nexo

private data class FilterItem(val key: String, val label: String, val amount: Double, val indent: Boolean = false)

/** Main-screen filter panel. */
@Composable
fun FilterPanel(
    ref: RefData,
    rows: List<TransactionEntity>,
    criteria: FilterCriteria,
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    onChange: (FilterCriteria) -> Unit,
) {
    val money = LocalMoneyColors.current
    val c = remember(ref) { Classifier(ref) }
    val all = Totals.fromRows(ref, rows)
    val filtered = Totals.fromRows(ref, rows.filter { criteria.active && criteria.matches(it, ref, c) })
    val summary = summaryText(ref, criteria)

    if (collapsed) {
        Box(Modifier.fillMaxSize()) {
            Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), tonalElevation = 6.dp, shadowElevation = 6.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(summary, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onCollapsedChange(false) }) { Text(stringResource(R.string.action_edit)) }
                }
            }
        }
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(1) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onCollapsedChange(true) }) { Text(stringResource(R.string.filter)) }
            }
            // Gauges strip
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Gauge(if (criteria.active && all.income != 0.0) filtered.income / all.income else 0.0, money.income)
                Spacer(Modifier.width(8.dp))
                Gauge(if (criteria.active && all.expense != 0.0) filtered.expense / all.expense else 0.0, money.expense)
                Spacer(Modifier.width(12.dp))
                val shown = if (criteria.active) filtered else all
                Column(Modifier.weight(1f)) {
                    Text(AmountFormatter.format(shown.income, ref.mainCurrency), color = money.income, style = MaterialTheme.typography.bodySmall)
                    Text(AmountFormatter.format(shown.expense, ref.mainCurrency), color = money.expense, style = MaterialTheme.typography.bodySmall)
                    Text(AmountFormatter.format(shown.total, ref.mainCurrency), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
            val dots = listOf(
                criteria.incomeAll || criteria.noneIncome || criteria.modifiedIncome || criteria.teIncome.isNotEmpty() || criteria.categories.any { ref.categoryById[it]?.kind == CategoryKind.INCOME },
                criteria.expenseAll || criteria.noneExpense || criteria.modifiedExpense || criteria.teExpense.isNotEmpty() || criteria.categories.any { ref.categoryById[it]?.kind == CategoryKind.EXPENSE } || criteria.otherOf.isNotEmpty(),
                criteria.accounts.isNotEmpty(),
            )
            val labels = listOf(R.string.income, R.string.expenses, R.string.account)
            SegmentedPill(
                options = listOf(0, 1, 2),
                selected = tab,
                onSelect = { tab = it },
                label = { stringResource(labels[it]) },
                accent = { i -> when (i) { 0 -> money.income; 1 -> money.expense; else -> null } },
                dot = { dots[it] },
                // The panel is itself a surface, so the track steps up to raised to stay visible.
                track = MaterialTheme.colorScheme.surfaceVariant,
                textStyle = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
            )
            when (tab) {
                0, 1 -> CategoryFilterList(ref, rows, c, if (tab == 0) CategoryKind.INCOME else CategoryKind.EXPENSE, criteria, expanded, { expanded = it }, onChange)
                else -> AccountFilterList(ref, rows, criteria, onChange)
            }
        }
    }
}

@Composable
private fun Gauge(fraction: Double, color: Color) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(44.dp)) {
            val stroke = Stroke(width = 6.dp.toPx())
            val inset = 3.dp.toPx()
            val size = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), size, style = stroke)
            drawArc(color, -90f, (fraction.coerceIn(0.0, 1.0) * 360).toFloat(), false, Offset(inset, inset), size, style = stroke)
        }
        Text("${(fraction.coerceIn(0.0, 1.0) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun summaryText(ref: RefData, f: FilterCriteria): String {
    if (!f.active) return stringResource(R.string.filter_hint)
    val parts = ArrayList<String>()
    if (f.accounts.isNotEmpty()) parts += f.accounts.mapNotNull { ref.accountById[it]?.name }.joinToString(", ")
    if (f.incomeAll) parts += stringResource(R.string.all_income)
    if (f.expenseAll) parts += stringResource(R.string.all_expenses)
    parts += f.categories.mapNotNull { id ->
        val cat = ref.categoryById[id] ?: return@mapNotNull null
        if (cat.parentId != null) "${ref.categoryName(cat.parentId)}(${cat.name})" else cat.name
    }
    parts += f.otherOf.mapNotNull { ref.categoryName(it)?.let { n -> "$n(${"…"})" } }
    if (f.noneIncome || f.noneExpense) parts += stringResource(R.string.none)
    if (f.modifiedIncome || f.modifiedExpense) parts += stringResource(R.string.modified_balance)
    parts += (f.teExpense + f.teIncome).mapNotNull { ref.accountById[it]?.name }
    return parts.filter { it.isNotBlank() }.joinToString(", ")
}

@Composable
private fun CategoryFilterList(
    ref: RefData,
    rows: List<TransactionEntity>,
    c: Classifier,
    kind: CategoryKind,
    f: FilterCriteria,
    expanded: Set<String>,
    onExpanded: (Set<String>) -> Unit,
    onChange: (FilterCriteria) -> Unit,
) {
    val txKind = if (kind == CategoryKind.INCOME) TxKind.INCOME else TxKind.EXPENSE
    val ofKind = rows.filter { it.kind == txKind }
    val byMain = ofKind.filter { !it.isBalanceAdjustment }.groupBy { ref.mainCategoryId(it.categoryId) }
    val sumOf = { list: List<TransactionEntity> -> list.sumOf { it.amountMain } }
    val allSelected = if (kind == CategoryKind.INCOME) f.incomeAll else f.expenseAll
    val mains = ref.categories.filter { it.kind == kind && it.parentId == null && (!it.isDeleted || byMain.containsKey(it.id)) }
    val noneSum = sumOf(byMain[null].orEmpty())
    val modSum = sumOf(ofKind.filter { it.isBalanceAdjustment })
    val flow = if (kind == CategoryKind.INCOME) CashFlow.INCOME else CashFlow.EXPENSE
    val teRows = rows.filter { it.kind == TxKind.TRANSFER_OUT && c.classify(it.kind, it.accountId, it.counterpartAccountId) == flow }
    val teBy = teRows.groupBy { if (kind == CategoryKind.INCOME) it.accountId else it.counterpartAccountId }
    val money = LocalMoneyColors.current
    LazyColumn {
        item {
            Row(Modifier.fillMaxWidth().clickable {
                onChange(if (kind == CategoryKind.INCOME) f.copy(incomeAll = !f.incomeAll) else f.copy(expenseAll = !f.expenseAll))
            }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(if (allSelected) ToggleableState.On else ToggleableState.Off, onClick = null, modifier = Modifier.padding(8.dp))
                Text(stringResource(R.string.all), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.this_month), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 12.dp))
            }
            HorizontalDivider()
        }
        items(mains, key = { it.id }) { m ->
            val subs = if (ref.settings.subcategories) ref.categories.filter { it.parentId == m.id && (!it.isDeleted || ofKind.any { t -> t.categoryId == it.id }) } else emptyList()
            Column {
                FilterRow(m.name, sumOf(byMain[m.id].orEmpty()), m.id in f.categories || allSelected, money.of(flow), expandable = subs.isNotEmpty(), expanded = m.id in expanded,
                    onExpand = { onExpanded(if (m.id in expanded) expanded - m.id else expanded + m.id) }) {
                    onChange(f.copy(categories = toggle(f.categories, m.id)))
                }
                if (m.id in expanded) {
                    subs.forEach { s ->
                        FilterRow(s.name, sumOf(ofKind.filter { it.categoryId == s.id }), s.id in f.categories || m.id in f.categories || allSelected, money.of(flow), indent = true) {
                            onChange(f.copy(categories = toggle(f.categories, s.id)))
                        }
                    }
                    FilterRow(stringResource(R.string.other), sumOf(ofKind.filter { it.categoryId == m.id }), m.id in f.otherOf || m.id in f.categories || allSelected, money.of(flow), indent = true) {
                        onChange(f.copy(otherOf = toggle(f.otherOf, m.id)))
                    }
                }
            }
        }
        if (noneSum != 0.0) item {
            val sel = if (kind == CategoryKind.INCOME) f.noneIncome else f.noneExpense
            FilterRow(stringResource(R.string.none), noneSum, sel || allSelected, money.of(flow)) {
                onChange(if (kind == CategoryKind.INCOME) f.copy(noneIncome = !f.noneIncome) else f.copy(noneExpense = !f.noneExpense))
            }
        }
        if (modSum != 0.0) item {
            val sel = if (kind == CategoryKind.INCOME) f.modifiedIncome else f.modifiedExpense
            FilterRow(stringResource(R.string.modified_balance), modSum, sel || allSelected, money.of(flow)) {
                onChange(if (kind == CategoryKind.INCOME) f.copy(modifiedIncome = !f.modifiedIncome) else f.copy(modifiedExpense = !f.modifiedExpense))
            }
        }
        teBy.forEach { (accId, list) ->
            if (accId == null) return@forEach
            item(key = "te_$accId") {
                val set = if (kind == CategoryKind.INCOME) f.teIncome else f.teExpense
                FilterRow("${ref.groupOf(accId)?.name ?: ""} · ${ref.accountById[accId]?.name ?: ""}", sumOf(list), accId in set || allSelected, money.of(flow)) {
                    onChange(if (kind == CategoryKind.INCOME) f.copy(teIncome = toggle(f.teIncome, accId)) else f.copy(teExpense = toggle(f.teExpense, accId)))
                }
            }
        }
    }
}

@Composable
private fun AccountFilterList(ref: RefData, rows: List<TransactionEntity>, f: FilterCriteria, onChange: (FilterCriteria) -> Unit) {
    val money = LocalMoneyColors.current
    val accounts = ref.orderedAccounts().filter { a -> ref.isAccountVisible(a) || rows.any { it.accountId == a.id } }
    LazyColumn {
        item {
            val allIds = accounts.map { it.id }.toSet()
            val state = when {
                f.accounts.isEmpty() -> ToggleableState.Off
                f.accounts.containsAll(allIds) -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            Row(Modifier.fillMaxWidth().clickable { onChange(f.copy(accounts = if (state == ToggleableState.On) emptySet() else allIds)) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TriStateCheckbox(state, onClick = null, modifier = Modifier.padding(8.dp))
                Text(stringResource(R.string.all), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider()
        }
        var group: String? = null
        accounts.forEach { a ->
            if (a.groupId != group) {
                group = a.groupId
                item(key = "g_${a.groupId}") {
                    Text(ref.groupById[a.groupId]?.name ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.nexo.primaryText, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                }
            }
            item(key = a.id) {
                val own = rows.filter { it.accountId == a.id }
                Row(Modifier.fillMaxWidth().clickable { onChange(f.copy(accounts = toggle(f.accounts, a.id))) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(a.id in f.accounts, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                    Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                        Text(AmountFormatter.format(own.filter { it.kind == TxKind.INCOME }.sumOf { it.amountMain }, ref.mainCurrency), color = money.income, style = MaterialTheme.typography.labelSmall)
                        Text(AmountFormatter.format(own.filter { it.kind == TxKind.EXPENSE }.sumOf { it.amountMain }, ref.mainCurrency), color = money.expense, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                        Text("↓ " + AmountFormatter.format(own.filter { it.kind == TxKind.TRANSFER_IN }.sumOf { it.amountMain }, ref.mainCurrency), style = MaterialTheme.typography.labelSmall)
                        Text("↑ " + AmountFormatter.format(own.filter { it.kind == TxKind.TRANSFER_OUT }.sumOf { it.amountMain }, ref.mainCurrency), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    amount: Double,
    checked: Boolean,
    color: Color,
    indent: Boolean = false,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onExpand: () -> Unit = {},
    onToggle: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = if (indent) 32.dp else 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(AmountFormatter.number(amount, 2), color = color, style = MaterialTheme.typography.bodySmall)
        if (expandable) TextButton(onClick = onExpand) { Text(if (expanded) "▲" else "▼") }
    }
}

private fun toggle(set: Set<String>, id: String) = if (id in set) set - id else set + id
