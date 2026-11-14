package com.nexo.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.TotalsBar
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.SearchKey
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.TxRow
import com.nexo.app.ui.transactions.selectedNet

@Composable
fun SearchScreen(key: SearchKey, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val c = state.criteria
    val r = state.result
    val ref = state.ref
    val selection = state.selection
    val suggestions = state.suggestions
    val nav = LocalNavigator.current
    val money = LocalMoneyColors.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var panel by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showMemos by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    LaunchedEffect(Unit) {
        vm.execute()
        requester.requestFocus()
    }
    BackHandler(enabled = selection.isNotEmpty()) { vm.setSelection(emptySet()) }

    Scaffold(topBar = {
        if (selection.isNotEmpty()) {
            SelectionBar(
                count = selection.size, total = r.rows.size, net = selectedNet(r.rows.map { it.second }, selection), main = ref.mainCurrency,
                onToggleAll = { vm.setSelection(if (selection.size >= r.rows.size) emptySet() else r.rows.map { it.second.id }.toSet()) },
                onClose = { vm.setSelection(emptySet()) },
                onAction = { bulkAction = it; bulkOpen = true },
            )
        } else {
            NexoTopBar(stringResource(R.string.search), onBack = { nav.back() }, titleContent = if (c.period == SearchPeriod.TOTAL || c.period == SearchPeriod.PERIOD) null else ({
                val label = when (c.period) {
                    SearchPeriod.WEEKLY -> Formats.range(r.range.start, r.range.end)
                    SearchPeriod.MONTHLY -> Formats.monthYear(ref.settings.periods.monthPeriodContaining(c.anchor).label)
                    else -> ref.settings.periods.yearOf(c.anchor).toString()
                }
                PeriodNavigator(label, { vm.step(false) }, { vm.step(true) })
            })) {
                AssistChip(onClick = { dialog = "period" }, label = { Text(stringResource(periodLabel(c.period))) }, trailingIcon = { Icon(Icons.Filled.ExpandMore, null) })
                IconButton(onClick = { panel = !panel }) { Icon(Icons.Outlined.Tune, stringResource(R.string.filter)) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (c.period == SearchPeriod.PERIOD) {
                DateRangeChips(c.from, c.to, onChange = { f, t -> vm.update { it.copy(from = f, to = t) } })
            }
            OutlinedTextField(
                value = c.query,
                onValueChange = vm::setQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (c.query.isNotEmpty()) ({ IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) } }) else null,
                placeholder = { Text(stringResource(R.string.search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.execute(); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).focusRequester(requester).onFocusChanged { focused = it.isFocused },
            )
            if (focused && c.query.isNotEmpty()) {
                val q = c.query
                suggestions.mapNotNull { s -> s.note.indexOf(q, ignoreCase = true).takeIf { it >= 0 && s.note != q }?.let { s to it } }
                    .sortedWith(compareByDescending<Pair<com.nexo.app.ui.entry.NoteSuggestion, Int>> { it.first.count }.thenBy { it.second }.thenByDescending { it.first.lastDate })
                    .take(5).forEach { (s, i) ->
                        Text(
                            buildAnnotatedString {
                                append(s.note.substring(0, i))
                                withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)) { append(s.note.substring(i, i + q.length)) }
                                append(s.note.substring(i + q.length))
                            },
                            Modifier.fillMaxWidth().clickable { vm.setQuery(s.note); vm.execute(); focus.clearFocus() }.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
            }
            if (panel) {
                CriteriaRow(stringResource(R.string.period), stringResource(periodLabel(c.period)), null) { dialog = "period" }
                CriteriaRow(stringResource(R.string.account), c.accounts.mapNotNull { ref.accountById[it]?.name }.joinToString(", ").ifEmpty { null }, { vm.update { it.copy(accounts = emptySet()) } }) { dialog = "account" }
                CriteriaRow(stringResource(R.string.category), categorySummary(ref, c), { vm.update { it.copy(categories = com.nexo.app.ui.transactions.FilterCriteria()) } }) { dialog = "category" }
                CriteriaRow(
                    stringResource(R.string.amount),
                    if (c.min == null && c.max == null) null else "${c.min?.let { AmountFormatter.number(it, ref.mainCurrency.decimals) } ?: stringResource(R.string.min)} ~ ${c.max?.let { AmountFormatter.number(it, ref.mainCurrency.decimals) } ?: stringResource(R.string.max)}",
                    { vm.update { it.copy(min = null, max = null) } },
                ) { dialog = "min" }
                CriteriaRow(stringResource(R.string.tags), c.tags.mapNotNull { ref.tagById[it]?.name }.joinToString(", ").ifEmpty { null }, { vm.update { it.copy(tags = emptySet()) } }) { dialog = "tag" }
                HorizontalDivider()
            }
            if (r.memos.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp)) {
                    FilterChip(!showMemos, onClick = { showMemos = false }, label = { Text(stringResource(R.string.transactions_n, r.rows.size)) })
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    FilterChip(showMemos, onClick = { showMemos = true }, label = { Text(stringResource(R.string.memos_n, r.memos.size)) })
                }
            }
            val memoMode = (showMemos && r.memos.isNotEmpty()) || (r.rows.isEmpty() && r.memos.isNotEmpty())
            if (!memoMode && r.rows.isNotEmpty()) {
                TotalsBar(r.income, r.expense, ref.mainCurrency, money.income, money.expense,
                    labels = Triple(stringResource(R.string.income), stringResource(R.string.expenses), stringResource(R.string.transfer)), totalValue = r.transfer)
            }
            val dark = LocalIsDark.current
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (memoMode) {
                    items(r.memos, key = { it.id }) { m ->
                        Card(onClick = { nav.navigate(MemoEditorKey(memoId = m.id)) }, colors = CardDefaults.cardColors(containerColor = NexoPalette.memo(m.color, dark)), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.date, style = MaterialTheme.typography.labelSmall)
                                if (m.title.isNotBlank()) Text(m.title, fontWeight = FontWeight.Bold)
                                Text(m.content.trimEnd(), maxLines = 3)
                            }
                        }
                    }
                } else {
                    if (r.loaded && r.rows.isEmpty()) item { com.nexo.app.ui.designsystem.component.EmptyState() }
                    items(r.rows, key = { it.second.id }) { (date, row) ->
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            Text(date.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
                            TxRow(
                                row, row.id in selection,
                                onClick = { if (selection.isNotEmpty()) vm.toggleSelect(row.id) else nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = row.id)) },
                                onLongClick = { vm.toggleSelect(row.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, vm.bulkActions, onDismiss = { bulkOpen = false })
    when (dialog) {
        "period" -> ChoiceDialog(
            title = stringResource(R.string.period), options = SearchPeriod.entries, selected = c.period, label = { stringResource(periodLabel(it)) },
            onSelect = { p -> vm.update { it.copy(period = p) } }, onDismiss = { dialog = null },
        )
        "account" -> MultiSelectDialog(stringResource(R.string.tab_accounts), accountItems(ref), c.accounts, onConfirm = { s -> vm.update { it.copy(accounts = s) } }, onDismiss = { dialog = null })
        "tag" -> MultiSelectDialog(stringResource(R.string.tags), ref.tags.filter { !it.isDeleted }.map { CheckItem(it.id, it.name) }, c.tags, onConfirm = { s -> vm.update { it.copy(tags = s) } }, onDismiss = { dialog = null })
        "category" -> CategoryCriteriaDialog(ref, c.categories, onConfirm = { f -> vm.update { it.copy(categories = f) } }, onDismiss = { dialog = null })
        "min" -> AmountSheet(stringResource(R.string.min), c.min, ref.mainCurrency.decimals, allowSign = false, onConfirm = { v -> vm.update { it.copy(min = v) }; dialog = "max" }, onDismiss = { if (dialog == "min") dialog = null })
        "max" -> AmountSheet(stringResource(R.string.max), c.max, ref.mainCurrency.decimals, allowSign = false, onConfirm = { v -> vm.update { it.copy(max = v) } }, onDismiss = { dialog = null })
    }
}

@Composable
private fun categorySummary(ref: com.nexo.app.core.model.RefData, c: SearchCriteria): String? {
    val f = c.categories
    if (!f.hasCategoryPart) return null
    val parts = buildList {
        if (f.incomeAll) add(stringResource(R.string.all_income))
        if (f.expenseAll) add(stringResource(R.string.all_expenses))
        f.categories.mapNotNull { ref.categoryById[it]?.name }.forEach { add(it) }
    }
    return parts.joinToString(", ")
}

fun periodLabel(p: SearchPeriod): Int = when (p) {
    SearchPeriod.TOTAL -> R.string.total
    SearchPeriod.WEEKLY -> R.string.weekly
    SearchPeriod.MONTHLY -> R.string.monthly
    SearchPeriod.ANNUALLY -> R.string.annually
    SearchPeriod.PERIOD -> R.string.period
}

@Composable
private fun CriteriaRow(label: String, value: String?, onClear: (() -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) { Text(value ?: "", maxLines = 1) }
        if (value != null && onClear != null) IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) }
    }
}
