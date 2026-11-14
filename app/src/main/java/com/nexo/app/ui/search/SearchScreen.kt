package com.nexo.app.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.model.CashFlow
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.PeriodNavigator
import com.nexo.app.ui.designsystem.component.TotalsBar
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.NexoPalette
import com.nexo.app.ui.entry.NoteSuggestion
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MemoEditorKey
import com.nexo.app.ui.navigation.SearchKey
import com.nexo.app.ui.transactions.BulkAction
import com.nexo.app.ui.transactions.BulkDialogs
import com.nexo.app.ui.transactions.BulkEditActions
import com.nexo.app.ui.transactions.FilterCriteria
import com.nexo.app.ui.transactions.SelectionBar
import com.nexo.app.ui.transactions.TxRow
import com.nexo.app.ui.transactions.TxRowUi
import com.nexo.app.ui.transactions.selectedNet

@Composable
fun SearchRoute(key: SearchKey, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    LaunchedEffect(Unit) { vm.execute() }
    SearchScreen(
        state = state,
        bulkActions = vm.bulkActions,
        onBack = nav::back,
        onStep = vm::step,
        onSetQuery = vm::setQuery,
        onExecute = vm::execute,
        onUpdateCriteria = vm::update,
        onToggleSelect = vm::toggleSelect,
        onSetSelection = vm::setSelection,
        onOpenMemo = { nav.navigate(MemoEditorKey(memoId = it)) },
        onOpenTransaction = { nav.navigate(EntryKey(mode = EntryMode.EDIT, txId = it)) },
    )
}

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    bulkActions: BulkEditActions,
    onBack: () -> Unit,
    onStep: (forward: Boolean) -> Unit,
    onSetQuery: (String) -> Unit,
    onExecute: () -> Unit,
    onUpdateCriteria: ((SearchCriteria) -> SearchCriteria) -> Unit,
    onToggleSelect: (txId: String) -> Unit,
    onSetSelection: (Set<String>) -> Unit,
    onOpenMemo: (memoId: String) -> Unit,
    onOpenTransaction: (txId: String) -> Unit,
) {
    val c = state.criteria
    val r = state.result
    val ref = state.ref
    val selection = state.selection
    val suggestions = state.suggestions
    val money = LocalMoneyColors.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var panel by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    var showMemos by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var bulkOpen by remember { mutableStateOf(false) }
    var bulkAction by remember { mutableStateOf<BulkAction?>(null) }
    LaunchedEffect(Unit) { requester.requestFocus() }
    BackHandler(enabled = selection.isNotEmpty()) { onSetSelection(emptySet()) }

    Scaffold(topBar = {
        if (selection.isNotEmpty()) {
            SelectionBar(
                count = selection.size,
                total = r.rows.size,
                net = selectedNet(
                    r.rows.map {
                        it.second
                    },
                    selection,
                ),
                main = ref.mainCurrency,
                onToggleAll = {
                    onSetSelection(
                        if (selection.size >= r.rows.size) {
                            emptySet()
                        } else {
                            r.rows.map { it.second.id }.toSet()
                        },
                    )
                },
                onClose = { onSetSelection(emptySet()) },
                onAction = {
                    bulkAction = it
                    bulkOpen = true
                },
            )
        } else {
            NexoTopBar(
                stringResource(R.string.search),
                onBack = onBack,
                titleContent = if (c.period ==
                    SearchPeriod.TOTAL ||
                    c.period == SearchPeriod.PERIOD
                ) {
                    null
                } else {
                    (
                        {
                            val label = when (c.period) {
                                SearchPeriod.WEEKLY -> Formats.range(r.range.start, r.range.end)

                                SearchPeriod.MONTHLY -> Formats.monthYear(
                                    ref.settings.periods.monthPeriodContaining(c.anchor).label,
                                )

                                else -> ref.settings.periods.yearOf(c.anchor).toString()
                            }
                            PeriodNavigator(label, { onStep(false) }, { onStep(true) })
                        }
                        )
                },
            ) {
                AssistChip(onClick = {
                    dialog = "period"
                }, label = {
                    Text(stringResource(periodLabel(c.period)))
                }, trailingIcon = { Icon(Icons.Filled.ExpandMore, null) })
                IconButton(onClick = {
                    panel = !panel
                }) { Icon(Icons.Outlined.Tune, stringResource(R.string.filter)) }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (c.period == SearchPeriod.PERIOD) {
                DateRangeChips(c.from, c.to, onChange = { f, t ->
                    onUpdateCriteria { it.copy(from = f, to = t) }
                })
            }
            OutlinedTextField(
                value = c.query,
                onValueChange = onSetQuery,
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (c.query.isNotEmpty()) {
                    (
                        {
                            IconButton(onClick = {
                                onSetQuery("")
                            }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) }
                        }
                        )
                } else {
                    null
                },
                placeholder = { Text(stringResource(R.string.search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    onExecute()
                    focus.clearFocus()
                }),
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = 12.dp,
                    vertical = 6.dp,
                ).focusRequester(requester).onFocusChanged {
                    focused = it.isFocused
                },
            )
            if (focused && c.query.isNotEmpty()) {
                val q = c.query
                suggestions.mapNotNull { s ->
                    s.note.indexOf(q, ignoreCase = true).takeIf {
                        it >= 0 &&
                            s.note != q
                    }?.let { s to it }
                }
                    .sortedWith(
                        compareByDescending<Pair<NoteSuggestion, Int>> {
                            it.first.count
                        }.thenBy { it.second }.thenByDescending { it.first.lastDate },
                    )
                    .take(5).forEach { (s, i) ->
                        Text(
                            buildAnnotatedString {
                                append(s.note.substring(0, i))
                                withStyle(
                                    SpanStyle(
                                        color = Color(0xFFE53935),
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                ) {
                                    append(s.note.substring(i, i + q.length))
                                }
                                append(s.note.substring(i + q.length))
                            },
                            Modifier.fillMaxWidth().clickable {
                                onSetQuery(s.note)
                                onExecute()
                                focus.clearFocus()
                            }.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
            }
            if (panel) {
                CriteriaRow(
                    stringResource(R.string.period),
                    stringResource(periodLabel(c.period)),
                    null,
                ) {
                    dialog = "period"
                }
                CriteriaRow(
                    stringResource(R.string.account),
                    c.accounts.mapNotNull {
                        ref.accountById[it]?.name
                    }.joinToString(", ").ifEmpty {
                        null
                    },
                    { onUpdateCriteria { it.copy(accounts = emptySet()) } },
                ) {
                    dialog = "account"
                }
                CriteriaRow(stringResource(R.string.category), categorySummary(ref, c), {
                    onUpdateCriteria { it.copy(categories = FilterCriteria()) }
                }) {
                    dialog = "category"
                }
                CriteriaRow(
                    stringResource(R.string.amount),
                    if (c.min == null && c.max == null) {
                        null
                    } else {
                        "${c.min?.let {
                            AmountFormatter.number(it, ref.mainCurrency.decimals)
                        } ?: stringResource(R.string.min)} ~ ${c.max?.let {
                            AmountFormatter.number(it, ref.mainCurrency.decimals)
                        } ?: stringResource(R.string.max)}"
                    },
                    { onUpdateCriteria { it.copy(min = null, max = null) } },
                ) { dialog = "min" }
                CriteriaRow(
                    stringResource(R.string.tags),
                    c.tags.mapNotNull {
                        ref.tagById[it]?.name
                    }.joinToString(", ").ifEmpty {
                        null
                    },
                    { onUpdateCriteria { it.copy(tags = emptySet()) } },
                ) {
                    dialog = "tag"
                }
                HorizontalDivider()
            }
            if (r.memos.isNotEmpty()) {
                Row(Modifier.padding(horizontal = 12.dp)) {
                    FilterChip(!showMemos, onClick = {
                        showMemos = false
                    }, label = { Text(stringResource(R.string.transactions_n, r.rows.size)) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(showMemos, onClick = {
                        showMemos = true
                    }, label = { Text(stringResource(R.string.memos_n, r.memos.size)) })
                }
            }
            val memoMode =
                (showMemos && r.memos.isNotEmpty()) || (r.rows.isEmpty() && r.memos.isNotEmpty())
            if (!memoMode && r.rows.isNotEmpty()) {
                TotalsBar(
                    r.income,
                    r.expense,
                    ref.mainCurrency,
                    money.income,
                    money.expense,
                    labels = Triple(
                        stringResource(R.string.income),
                        stringResource(R.string.expenses),
                        stringResource(R.string.transfer),
                    ),
                    totalValue = r.transfer,
                )
            }
            val dark = LocalIsDark.current
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (memoMode) {
                    items(r.memos, key = { it.id }) { m ->
                        Card(
                            onClick = {
                                onOpenMemo(m.id)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = NexoPalette.memo(m.color, dark),
                            ),
                            modifier = Modifier.fillMaxWidth().padding(
                                horizontal = 12.dp,
                                vertical = 4.dp,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(m.date, style = MaterialTheme.typography.labelSmall)
                                if (m.title.isNotBlank()) {
                                    Text(m.title, fontWeight = FontWeight.Bold)
                                }
                                Text(m.content.trimEnd(), maxLines = 3)
                            }
                        }
                    }
                } else {
                    if (r.loaded && r.rows.isEmpty()) item { EmptyState() }
                    items(r.rows, key = { it.second.id }) { (date, row) ->
                        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                            Text(
                                date.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(start = 16.dp, top = 6.dp),
                            )
                            TxRow(
                                row,
                                row.id in selection,
                                onClick = {
                                    if (selection.isNotEmpty()) {
                                        onToggleSelect(row.id)
                                    } else {
                                        onOpenTransaction(row.id)
                                    }
                                },
                                onLongClick = { onToggleSelect(row.id) },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
    BulkDialogs(bulkAction, bulkOpen, selection.size, ref, bulkActions, onDismiss = {
        bulkOpen = false
    })
    when (dialog) {
        "period" -> ChoiceDialog(
            title = stringResource(R.string.period),
            options = SearchPeriod.entries,
            selected = c.period,
            label = {
                stringResource(periodLabel(it))
            },
            onSelect = { p -> onUpdateCriteria { it.copy(period = p) } },
            onDismiss = { dialog = null },
        )

        "account" -> MultiSelectDialog(
            stringResource(R.string.tab_accounts),
            accountItems(ref),
            c.accounts,
            onConfirm = { s ->
                onUpdateCriteria { it.copy(accounts = s) }
            },
            onDismiss = {
                dialog = null
            },
        )

        "tag" -> MultiSelectDialog(
            stringResource(R.string.tags),
            ref.tags.filter {
                !it.isDeleted
            }.map {
                CheckItem(it.id, it.name)
            },
            c.tags,
            onConfirm = { s -> onUpdateCriteria { it.copy(tags = s) } },
            onDismiss = {
                dialog = null
            },
        )

        "category" -> CategoryCriteriaDialog(ref, c.categories, onConfirm = { f ->
            onUpdateCriteria { it.copy(categories = f) }
        }, onDismiss = {
            dialog = null
        })

        "min" -> AmountSheet(
            stringResource(R.string.min),
            c.min,
            ref.mainCurrency.decimals,
            allowSign = false,
            onConfirm = { v ->
                onUpdateCriteria { it.copy(min = v) }
                dialog = "max"
            },
            onDismiss = { if (dialog == "min") dialog = null },
        )

        "max" -> AmountSheet(
            stringResource(R.string.max),
            c.max,
            ref.mainCurrency.decimals,
            allowSign = false,
            onConfirm = { v ->
                onUpdateCriteria { it.copy(max = v) }
            },
            onDismiss = {
                dialog = null
            },
        )
    }
}

@Composable
private fun categorySummary(ref: RefData, c: SearchCriteria): String? {
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
private fun CriteriaRow(
    label: String,
    value: String?,
    onClear: (() -> Unit)?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(
            onClick = onClick,
        ).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(96.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) { Text(value ?: "", maxLines = 1) }
        if (value != null && onClear != null) {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_clear))
            }
        }
    }
}

private fun previewRow(id: String, category: String, note: String, amount: Double) = TxRowUi(
    id = id,
    tx = null,
    category = UiText.Raw(category),
    subCategory = null,
    title = UiText.Raw(note),
    titleIsNote = true,
    accountText = UiText.Raw("Wallet"),
    time = null,
    tags = null,
    descriptionLine = null,
    hasPhoto = false,
    flow = CashFlow.EXPENSE,
    amount = amount,
    amountText = AmountFormatter.format(amount, PreviewData.ref.mainCurrency),
    amountMain = amount,
    visual = PreviewData.ref.visual(category.lowercase()),
)

@Preview
@Composable
private fun SearchScreenPreview() = NexoPreview {
    SearchScreen(
        state = SearchUiState(
            ref = PreviewData.ref,
            criteria = SearchCriteria(query = "lunch", anchor = PreviewData.today),
            result = SearchResult(
                loaded = true,
                rows = listOf(
                    PreviewData.today to previewRow("t1", "Food", "Lunch with team", 1_850.0),
                    PreviewData.today.minusDays(3) to previewRow("t2", "Food", "Lunch", 950.0),
                ),
                expense = 2_800.0,
            ),
        ),
        bulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> }),
        onBack = {},
        onStep = {},
        onSetQuery = {},
        onExecute = {},
        onUpdateCriteria = {},
        onToggleSelect = {},
        onSetSelection = {},
        onOpenMemo = {},
        onOpenTransaction = {},
    )
}
