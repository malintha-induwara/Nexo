package com.nexo.app.ui.sms

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.data.sms.ledgerAmount
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.DateRangeChips
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.InlineBottomSheet
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsHistoryKey

/** "Add past transactions": nothing selected by default, picked rows go straight to the ledger. */
@Composable
fun SmsHistoryScreen(key: SmsHistoryKey) {
    val vm = hiltViewModel<SmsHistoryViewModel, SmsHistoryViewModel.Factory>(creationCallback = { it.create(key) })
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ref = state.ref
    val range = state.range
    val custom = state.custom
    val rows = state.rows
    val kind = state.kind
    val card = state.card
    val selection = state.selection
    val adding = state.adding
    val feePrompt = state.feePrompt
    val shownPrompt = remember { arrayOfNulls<FeePrompt>(1) }
    if (feePrompt != null) shownPrompt[0] = feePrompt
    var mapping by remember { mutableStateOf<CardRef?>(null) }
    var mappingOpen by remember { mutableStateOf(false) }
    val now = remember { System.currentTimeMillis() }

    LaunchedEffect(Unit) { vm.initialize() }
    state.result?.let { r ->
        LaunchedEffect(r) {
            r.message?.let { Toast.makeText(context, it.asString(resources), Toast.LENGTH_LONG).show() }
            vm.onResultHandled()
            nav.back()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), { nav.back() })
                Spacer(Modifier.width(Space.m))
                Text(stringResource(R.string.sms_history_title), style = MaterialTheme.typography.titleLarge, color = colors.text)
            }
            ChipRow(
                // Opened from the re-enable hint, the gap comes first so the chosen chip is in view.
                options = if (state.gap) listOf(HistoryRange.GAP) + HistoryRange.entries.filter { it != HistoryRange.GAP }
                else HistoryRange.entries.filter { it != HistoryRange.GAP },
                selected = range,
                label = { stringResource(it.label) },
                onSelect = { haptics.tick(); vm.setRange(it) },
            )
            if (range == HistoryRange.CUSTOM) {
                DateRangeChips(custom.first, custom.second, vm::setCustom, color = colors.text)
            }

            val list = rows
            when {
                list == null -> Text(
                    stringResource(R.string.sms_history_loading),
                    Modifier.fillMaxWidth().padding(top = Space.xxxl),
                    style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                list.isEmpty() -> EmptyState(
                    stringResource(if (state.permitted) R.string.sms_history_empty else R.string.sms_history_needs_permission),
                    Modifier.padding(top = Space.xxl),
                )
                else -> {
                    val cards = list.mapNotNull { it.item.cardLast4 }.distinct().sorted()
                    ChipRow(
                        options = HistoryKind.entries,
                        selected = kind,
                        label = { stringResource(it.label) },
                        onSelect = { haptics.tick(); vm.setKind(it) },
                    )
                    if (cards.size > 1) {
                        ChipRow(
                            options = listOf<String?>(null) + cards,
                            selected = card,
                            label = { it?.let { c -> stringResource(R.string.sms_history_card, c) } ?: stringResource(R.string.sms_history_kind_all) },
                            onSelect = { haptics.tick(); vm.setCard(it) },
                        )
                    }
                    val visible = state.visible
                    val unmapped = list.filter { it.item.accountId == null && it.item.cardLast4 != null && !it.cancelled }
                        .map { CardRef(it.item.bank, it.item.cardLast4!!) }.distinct()
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Space.xl)) {
                        unmapped.firstOrNull()?.let { c ->
                            item(key = "card_${c.bank}_${c.last4}") {
                                CardQuestion(c, Modifier.animateItem()) {
                                    mapping = c
                                    mappingOpen = true
                                }
                            }
                        }
                        item(key = "select_all") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                                val all = visible.filter { !it.cancelled }.all { it.item.id in selection }
                                Text(
                                    if (selection.isEmpty()) "" else pluralStringResource(R.plurals.sms_history_selected, selection.size, selection.size),
                                    Modifier.weight(1f), style = NexoType.Meta, color = colors.textTertiary,
                                )
                                TextButton(onClick = { haptics.tick(); vm.selectAll(visible) }) {
                                    Text(stringResource(if (all && visible.isNotEmpty()) R.string.sms_history_clear else R.string.sms_history_select_all), color = colors.text)
                                }
                            }
                        }
                        items(visible, key = { it.item.id }) { row ->
                            Box(Modifier.animateItem()) {
                                SmsQueueRow(
                                    item = row.item,
                                    ref = ref,
                                    selected = row.item.id in selection,
                                    now = now,
                                    onClick = { haptics.tick(); vm.toggle(row) },
                                    onLongClick = null,
                                    onAmountClick = null,
                                    note = if (row.possiblyAdded && !row.cancelled) stringResource(R.string.sms_history_possibly_added) else null,
                                )
                            }
                        }
                    }
                }
            }

            if (selection.isNotEmpty()) {
                val chosen = list.orEmpty().filter { it.item.id in selection }
                val total = chosen.sumOf { ledgerAmount(it.item) }
                Button(
                    onClick = { haptics.confirm(); vm.add() },
                    enabled = !adding,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.m).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text(pluralStringResource(R.plurals.sms_history_add, selection.size, selection.size), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(Space.s))
                    Text("· " + AmountFormatter.format(total, ref.mainCurrency), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        InlineBottomSheet(mappingOpen, onDismiss = { mappingOpen = false }, heightFraction = 0.72f) {
            mapping?.let { c ->
                SheetTitle(
                    stringResource(R.string.sms_card_question, com.nexo.app.data.sms.parser.Banks.byName(c.bank)?.displayName.orEmpty(), c.last4),
                    stringResource(R.string.sms_card_hint),
                )
                AccountPickList(ref, null) { id ->
                    vm.mapCard(c, id)
                    mappingOpen = false
                }
            }
        }
        InlineBottomSheet(visible = feePrompt != null, onDismiss = { vm.answerFee(null) }, heightFraction = 0.6f) {
            shownPrompt[0]?.let { p -> FeePromptContent(p, ref) { vm.answerFee(it) } }
        }
    }
}

/** A horizontally scrolling row of filter chips (the same look as the queue's filters). */
@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        options.forEach { option ->
            val on = option == selected
            val container by animateColorAsState(if (on) colors.primarySoft else colors.raised, NexoMotion.standard(), label = "chip")
            val content by animateColorAsState(if (on) colors.primaryText else colors.textSecondary, NexoMotion.standard(), label = "chipText")
            Text(
                label(option),
                Modifier.clip(NexoShapes.pill).background(container).clickable { onSelect(option) }.padding(horizontal = Space.m + Space.xxs, vertical = Space.s),
                style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1,
            )
        }
    }
}
