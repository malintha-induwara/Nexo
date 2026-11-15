package com.nexo.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.CatalogCurrency
import com.nexo.app.core.common.money.CurrencyCatalog
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.nexo
import com.nexo.app.ui.navigation.AddSubCurrencyKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.MainCurrencyPickerKey
import com.nexo.app.ui.navigation.SubCurrencyEditKey

private val DECIMALS = listOf(0, 1, 2, 3, 4, 5, 8)

@Composable
private fun decimalsLabel(d: Int) = if (d == 0) {
    stringResource(R.string.none)
} else {
    "1." + "0".repeat(d)
}

@Composable
private fun FormatRows(
    position: SymbolPosition,
    decimals: Int,
    symbol: String,
    onPosition: (SymbolPosition) -> Unit,
    onDecimals: (Int) -> Unit,
) {
    var pick by remember { mutableStateOf(false) }
    Text(
        stringResource(R.string.unit_position),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
    )
    listOf(SymbolPosition.PREFIX, SymbolPosition.SUFFIX).forEach { p ->
        Row(
            Modifier.fillMaxWidth().clickable {
                onPosition(p)
            }.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(position == p, onClick = null, modifier = Modifier.padding(8.dp))
            Text(
                stringResource(
                    if (p == SymbolPosition.PREFIX) {
                        R.string.position_front
                    } else {
                        R.string.position_end
                    },
                ) +
                    "  " +
                    AmountFormatter.format(1000.0, symbol, p, decimals),
            )
        }
    }
    SettingRow(stringResource(R.string.decimal_point), decimalsLabel(decimals), onClick = {
        pick = true
    })
    Text(
        stringResource(R.string.preview) + ": " +
            AmountFormatter.format(1000.0, symbol, position, decimals),
        modifier = Modifier.padding(16.dp),
        fontWeight = FontWeight.SemiBold,
    )
    if (pick) {
        ChoiceDialog(stringResource(R.string.decimal_point), DECIMALS, decimals, {
            decimalsLabel(it)
        }, onSelect = onDecimals, onDismiss = {
            pick = false
        })
    }
}

/** The message toast and the way back once a main-currency change is written. */
@Composable
private fun MainCurrencyEffects(
    state: MainCurrencyUiState,
    onMessageShown: (id: Long) -> Unit,
    onClosed: () -> Unit,
) {
    val effectContext = LocalContext.current
    state.userMessage?.let { message ->
        val text = message.text.asString()
        LaunchedEffect(message.id) {
            Toast.makeText(effectContext, text, Toast.LENGTH_LONG).show()
            onMessageShown(message.id)
        }
    }
    LaunchedEffect(state.closed) {
        if (state.closed) onClosed()
    }
}

@Composable
fun MainCurrencyRoute(vm: MainCurrencyViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    MainCurrencyEffects(state, onMessageShown = vm::onUserMessageShown, onClosed = {
        nav.back()
        vm.onClosedHandled()
    })
    MainCurrencyScreen(
        state = state,
        onBack = nav::back,
        onChange = { nav.navigate(MainCurrencyPickerKey) },
        onSave = vm::setMainFormat,
    )
}

@Composable
internal fun MainCurrencyScreen(
    state: MainCurrencyUiState,
    onBack: () -> Unit,
    onChange: () -> Unit,
    onSave: (SymbolPosition, decimals: Int) -> Unit,
) {
    val ref = state.ref
    val main = ref.mainCurrency
    var position by rememberSaveable(main.id) { mutableStateOf(main.symbolPosition) }
    var decimals by rememberSaveable(main.id) { mutableStateOf(main.decimals) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.main_currency_setting), onBack = onBack) {
            TextButton(onClick = onChange) { Text(stringResource(R.string.action_change)) }
        }
    }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            Text(
                main.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            FormatRows(position, decimals, main.symbol, { position = it }, { decimals = it })
            Button(onClick = {
                onSave(position, decimals)
            }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Composable
fun MainCurrencyPickerRoute(vm: MainCurrencyViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    MainCurrencyEffects(state, onMessageShown = vm::onUserMessageShown, onClosed = {
        nav.back()
        vm.onClosedHandled()
    })
    MainCurrencyPickerScreen(
        state = state,
        onBack = nav::back,
        onChangeWithRate = vm::changeMainWithRate,
        onChangeWithReset = vm::changeMainWithReset,
    )
}

/**
 * Picking a new main currency: [onChangeWithRate] keeps the data and converts it,
 * [onChangeWithReset] starts the book over.
 */
@Composable
internal fun MainCurrencyPickerScreen(
    state: MainCurrencyUiState,
    onBack: () -> Unit,
    onChangeWithRate: (CatalogCurrency, rate: Double) -> Unit,
    onChangeWithReset: (CatalogCurrency) -> Unit,
) {
    val ref = state.ref
    val busy = state.busy
    var target by remember { mutableStateOf<CatalogCurrency?>(null) }
    var askRate by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf<Double?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.main_currency_setting), onBack = onBack)
    }) { p ->
        LazyColumn(Modifier.padding(p)) {
            items(CurrencyCatalog.all, key = { it.code }) { c ->
                Text(
                    "${c.displayName()} (${c.mainSymbol})",
                    color = if (c.code == ref.mainCurrency.isoCode) {
                        MaterialTheme.nexo.primaryText
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (c.code != ref.mainCurrency.isoCode) {
                            target = c
                        }
                    }.padding(16.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    val t = target
    if (t != null && !askRate && !confirmReset && rate == null) {
        AlertDialog(
            onDismissRequest = { target = null },
            text = {
                Text(stringResource(R.string.main_change_question, "${t.code}(${t.mainSymbol})"))
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        askRate = true
                    }) { Text(stringResource(R.string.data_retention)) }
                    TextButton(onClick = {
                        confirmReset = true
                    }) { Text(stringResource(R.string.data_initialization)) }
                    TextButton(onClick = {
                        target = null
                    }) { Text(stringResource(R.string.action_cancel)) }
                }
            },
        )
    }
    if (askRate && t != null) {
        val existing = ref.currencyById[t.code]
        val initial = existing?.rate?.takeIf { it > 0 }?.let { 1.0 / it } ?: 1.0
        AmountSheet(
            title = stringResource(R.string.exchange_rate),
            subtitle = "1 ${ref.mainCurrency.isoCode ?: ref.mainCurrency.symbol} = R ${t.code}",
            initial = initial, decimals = 6, allowSign = false, validate = {
                (it ?: 0.0) > 0
            }, format = { AmountFormatter.rate(it) },
            onConfirm = { rate = it }, onDismiss = { askRate = false },
        )
    }
    rate?.let { r ->
        ConfirmDialog(stringResource(R.string.main_change_warning), onConfirm = {
            onChangeWithRate(t!!, r)
        }, onDismiss = {
            rate = null
            target = null
        })
    }
    if (confirmReset && t != null) {
        ConfirmDialog(stringResource(R.string.all_data_deleted_question), onConfirm = {
            onChangeWithReset(t)
        }, onDismiss = {
            confirmReset = false
            target = null
        })
    }
    if (busy) {
        AlertDialog(onDismissRequest = {}, text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
                Text(stringResource(R.string.main_changing), Modifier.padding(start = 16.dp))
            }
        }, confirmButton = {})
    }
}

@Composable
fun SubCurrenciesRoute(vm: SubCurrencyViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    SubCurrenciesScreen(
        state = state,
        onBack = nav::back,
        onAdd = { nav.navigate(AddSubCurrencyKey) },
        onEdit = { c -> nav.navigate(SubCurrencyEditKey(currencyId = c.id, custom = c.isCustom)) },
        onReorder = vm::reorder,
        onHide = vm::hide,
    )
}

@Composable
internal fun SubCurrenciesScreen(
    state: SubCurrencyUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Currency) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
    onHide: (id: String) -> Unit,
) {
    val ref = state.ref
    val subs = ref.activeCurrencies.drop(1)
    var hide by remember { mutableStateOf<Currency?>(null) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.sub_currency_setting), onBack = onBack) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, stringResource(R.string.action_add))
            }
        }
    }) { p ->
        if (subs.isEmpty()) {
            EmptyState(modifier = Modifier.padding(p))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(p)) {
            itemsIndexed(subs, key = { _, c -> c.id }) { i, c ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onEdit(c)
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        val head = if (c.isCustom) {
                            AmountFormatter.format(1.0, c)
                        } else {
                            "${c.isoCode} 1.00"
                        }
                        val main = ref.mainCurrency.isoCode ?: ref.mainCurrency.symbol
                        Text("$head = $main ${AmountFormatter.rate(c.rate)}")
                        Text(
                            c.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    IconButton(onClick = {
                        onReorder(
                            subs.map {
                                it.id
                            }.toMutableList().apply { add(i - 1, removeAt(i)) },
                        )
                    }, enabled = i > 0) {
                        Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up))
                    }
                    IconButton(onClick = {
                        onReorder(
                            subs.map {
                                it.id
                            }.toMutableList().apply { add(i + 1, removeAt(i)) },
                        )
                    }, enabled = i < subs.size - 1) {
                        Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down))
                    }
                    IconButton(onClick = {
                        hide = c
                    }) {
                        Icon(
                            Icons.Filled.RemoveCircle,
                            stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    hide?.let { c ->
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            onHide(c.id)
        }, onDismiss = {
            hide = null
        })
    }
}

@Composable
fun AddSubCurrencyRoute(vm: SubCurrencyViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    AddSubCurrencyScreen(
        state = state,
        onBack = nav::back,
        onAddCustom = { nav.navigate(SubCurrencyEditKey(custom = true)) },
        onPick = { code -> nav.replace(SubCurrencyEditKey(isoCode = code)) },
    )
}

/** Add a built-in sub-currency. */
@Composable
internal fun AddSubCurrencyScreen(
    state: SubCurrencyUiState,
    onBack: () -> Unit,
    onAddCustom: () -> Unit,
    onPick: (isoCode: String) -> Unit,
) {
    val ref = state.ref
    var query by remember { mutableStateOf("") }
    val list = CurrencyCatalog.all.filter { c ->
        c.code != ref.mainCurrency.isoCode &&
            ref.currencyById[c.code]?.isVisible != true
    }
        .filter { query.isBlank() || it.displayName().contains(query, true) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.add_sub_currency), onBack = onBack) {
            IconButton(onClick = onAddCustom) {
                Icon(Icons.Filled.Add, stringResource(R.string.custom_currency))
            }
        }
    }) { p ->
        Column(Modifier.padding(p)) {
            OutlinedTextField(query, {
                query = it
            }, placeholder = {
                Text(stringResource(R.string.search))
            }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(12.dp))
            LazyColumn {
                var letter: Char? = null
                list.forEach { c ->
                    if (c.code.first() != letter) {
                        letter = c.code.first()
                        item(key = "h_$letter") {
                            Text(
                                letter.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.nexo.primaryText,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                            )
                        }
                    }
                    item(key = c.code) {
                        Text(
                            "${c.displayName()} (${c.subSymbol})",
                            Modifier.fillMaxWidth().clickable {
                                onPick(c.code)
                            }.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubCurrencyEditRoute(key: SubCurrencyEditKey, vm: SubCurrencyViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.closed) {
        if (state.closed) {
            nav.back()
            vm.onClosedHandled()
        }
    }
    SubCurrencyEditScreen(key = key, state = state, onBack = nav::back, onSave = vm::saveSub)
}

/**
 * Edit / add a sub-currency, built-in or custom. [onSave] gets the catalog entry (null for a
 * custom one), the currency being edited if any, then the name, symbol, format and rate.
 */
@Composable
internal fun SubCurrencyEditScreen(
    key: SubCurrencyEditKey,
    state: SubCurrencyUiState,
    onBack: () -> Unit,
    onSave: (CatalogCurrency?, Currency?, String, String, SymbolPosition, Int, Double) -> Unit,
) {
    val ref = state.ref
    val existing = (key.currencyId ?: key.isoCode)?.let { ref.currencyById[it] }
    val catalog = CurrencyCatalog.find(key.isoCode ?: existing?.isoCode)
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var symbol by rememberSaveable { mutableStateOf(existing?.symbol ?: catalog?.subSymbol ?: "") }
    var position by rememberSaveable {
        mutableStateOf(existing?.symbolPosition ?: catalog?.position ?: SymbolPosition.PREFIX)
    }
    var decimals by rememberSaveable {
        mutableStateOf(existing?.decimals ?: catalog?.decimals ?: 2)
    }
    var rate by rememberSaveable { mutableStateOf(existing?.rate ?: 1.0) }
    var pad by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    val mainLabel = ref.mainCurrency.symbol
    Scaffold(topBar = {
        NexoTopBar(
            stringResource(
                if (key.custom) R.string.custom_currency else R.string.sub_currency_setting,
            ),
            onBack = onBack,
        )
    }) { p ->
        Column(Modifier.padding(p).verticalScroll(rememberScrollState())) {
            if (key.custom) {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        error = null
                    },
                    label = {
                        Text(stringResource(R.string.name))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                OutlinedTextField(
                    symbol,
                    {
                        symbol = it
                        error = null
                    },
                    label = {
                        Text(stringResource(R.string.symbol))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                Text(
                    catalog?.displayName() ?: existing?.name ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            SettingRow(
                stringResource(R.string.exchange_rate),
                "1 ${catalog?.code ?: symbol} = ${AmountFormatter.rate(rate)} $mainLabel",
                onClick = {
                    pad = true
                },
            )
            FormatRows(position, decimals, symbol.ifBlank { "¤" }, { position = it }, {
                decimals = it
            })
            error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Button(onClick = {
                when {
                    key.custom && name.isBlank() -> error = R.string.enter_name

                    key.custom && symbol.isBlank() -> error = R.string.enter_symbol

                    else -> onSave(
                        if (key.custom) null else catalog!!,
                        existing,
                        name,
                        symbol,
                        position,
                        decimals,
                        rate,
                    )
                }
            }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
    if (pad) {
        AmountSheet(stringResource(R.string.exchange_rate), rate, 10, allowSign = false, format = {
            AmountFormatter.rate(it)
        }, validate = {
            (it ?: 0.0) >
                0
        }, onConfirm = { rate = it ?: 1.0 }, onDismiss = { pad = false })
    }
}

@Preview
@Composable
private fun MainCurrencyScreenPreview() = NexoPreview {
    MainCurrencyScreen(
        state = MainCurrencyUiState(ref = PreviewData.ref),
        onBack = {},
        onChange = {},
        onSave = { _, _ -> },
    )
}

@Preview
@Composable
private fun MainCurrencyPickerScreenPreview() = NexoPreview {
    MainCurrencyPickerScreen(
        state = MainCurrencyUiState(ref = PreviewData.ref),
        onBack = {},
        onChangeWithRate = { _, _ -> },
        onChangeWithReset = {},
    )
}

@Preview
@Composable
private fun SubCurrenciesScreenPreview() = NexoPreview {
    SubCurrenciesScreen(
        state = SubCurrencyUiState(ref = PreviewData.ref),
        onBack = {},
        onAdd = {},
        onEdit = {},
        onReorder = {},
        onHide = {},
    )
}

@Preview
@Composable
private fun AddSubCurrencyScreenPreview() = NexoPreview {
    AddSubCurrencyScreen(
        state = SubCurrencyUiState(ref = PreviewData.ref),
        onBack = {},
        onAddCustom = {},
        onPick = {},
    )
}

@Preview
@Composable
private fun SubCurrencyEditScreenPreview() = NexoPreview {
    SubCurrencyEditScreen(
        key = SubCurrencyEditKey(currencyId = "USD"),
        state = SubCurrencyUiState(ref = PreviewData.ref),
        onBack = {},
        onSave = { _, _, _, _, _, _, _ -> },
    )
}
