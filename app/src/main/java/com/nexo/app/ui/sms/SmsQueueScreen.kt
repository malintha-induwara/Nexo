package com.nexo.app.ui.sms

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.settings.SmsHistoryImport
import com.nexo.app.sms.Certainty
import com.nexo.app.sms.SmsStatus
import com.nexo.app.sms.data.SmsItemEntity
import com.nexo.app.sms.needsAttention
import com.nexo.app.sms.needsDetails
import com.nexo.app.ui.components.AmountSheet
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.InlineBottomSheet
import com.nexo.app.ui.components.RoundIconButton
import com.nexo.app.ui.components.SwipeAction
import com.nexo.app.ui.components.SwipeActionRow
import com.nexo.app.ui.components.UndoBar
import com.nexo.app.ui.components.rememberHaptics
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsHistoryKey
import com.nexo.app.ui.navigation.SmsSettingsKey
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.theme.NexoShapes
import com.nexo.app.ui.theme.NexoType
import com.nexo.app.ui.theme.Space
import kotlinx.coroutines.delay

private sealed interface QueueSheet {
    data class MapCard(val card: CardRef) : QueueSheet
    data object Assign : QueueSheet
    data class Deny(val ids: Set<String>) : QueueSheet
}

/** The Review Queue (spec-money-master-sl 04 NQ-001/003). */
@Composable
fun SmsQueueScreen(vm: SmsQueueViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val unmapped by vm.unmappedCards.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val undo by vm.undo.collectAsStateWithLifecycle()
    val permitted by vm.permitted.collectAsStateWithLifecycle()
    val feePrompt by vm.feePrompt.collectAsStateWithLifecycle()
    // Keeps the last question drawn while the sheet slides away.
    val shownPrompt = remember { arrayOfNulls<FeePrompt>(1) }
    if (feePrompt != null) shownPrompt[0] = feePrompt

    var sheet by remember { mutableStateOf<QueueSheet?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editAmount by remember { mutableStateOf<SmsItemEntity?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    LifecycleResumeEffect(Unit) {
        vm.markSeen()
        onPauseOrDispose {}
    }
    BackHandler(selection.isNotEmpty()) { vm.clearSelection() }

    fun open(s: QueueSheet) {
        sheet = s
        sheetOpen = true
    }
    val selecting = selection.isNotEmpty()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(selecting, transitionSpec = { fadeIn(NexoMotion.enter()) togetherWith fadeOut(NexoMotion.exit()) }, label = "queueHeader") { sel ->
                if (sel) {
                    SelectionHeader(
                        count = selection.size,
                        onClose = vm::clearSelection,
                        onAssign = { open(QueueSheet.Assign) },
                        onDeny = { open(QueueSheet.Deny(selection)) },
                        onApprove = { haptics.confirm(); vm.approveSelected() },
                    )
                } else {
                    Row(Modifier.fillMaxWidth().height(56.dp).padding(start = Space.m, end = Space.gutter), verticalAlignment = Alignment.CenterVertically) {
                        RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), { nav.back() })
                        Spacer(Modifier.width(Space.m))
                        Text(stringResource(R.string.sms_queue_title), style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
                        CapturePill(
                            label = when {
                                device?.smsCaptureEnabled == false -> stringResource(R.string.sms_capture_off)
                                !permitted -> stringResource(R.string.sms_capture_needs_permission)
                                else -> stringResource(R.string.sms_capture_on)
                            },
                            on = device?.smsCaptureEnabled != false && permitted,
                        ) { nav.navigate(SmsSettingsKey) }
                    }
                }
            }
            FilterChips(
                selected = filter,
                pendingCount = pending.size,
                attentionCount = pending.count { it.needsAttention },
                onSelect = { haptics.tick(); vm.setFilter(it) },
            )
            val list = items
            val queueView = filter.status == SmsStatus.PENDING && !selecting
            val showCards = queueView && unmapped.isNotEmpty()
            val gapFrom = device?.smsGapFrom ?: 0L
            val gapUntil = device?.smsGapUntil ?: 0L
            val showGap = queueView && gapFrom > 0
            val showOffer = queueView && !showGap && permitted &&
                device?.smsHistoryImport.let { it == SmsHistoryImport.NOT_OFFERED || it == SmsHistoryImport.OFFERED }
            when {
                list == null -> Unit
                list.isEmpty() && !showCards && !showGap && !showOffer -> EmptyState(
                    stringResource(if (filter == QueueFilter.ALL) R.string.sms_empty else R.string.sms_empty_filter),
                    Modifier.padding(top = Space.xxxl),
                )
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 120.dp)) {
                    if (showGap) {
                        item(key = "gap") {
                            SmsPromptCard(
                                stringResource(R.string.sms_gap_hint), null, stringResource(R.string.sms_history_title),
                                { nav.navigate(SmsHistoryKey(gapFrom, gapUntil)) }, Modifier.animateItem(),
                                stringResource(R.string.sms_history_not_now), vm::clearGap,
                            )
                        }
                    }
                    if (showOffer) {
                        item(key = "offer") {
                            SmsPromptCard(
                                stringResource(R.string.sms_history_offer_title), stringResource(R.string.sms_history_offer_text),
                                stringResource(R.string.sms_history_choose), { nav.navigate(SmsHistoryKey()) }, Modifier.animateItem(),
                                stringResource(R.string.sms_history_not_now), vm::skipHistory,
                            )
                        }
                    }
                    if (showCards) {
                        val card = unmapped.first()
                        item(key = "card_${card.bank}_${card.last4}") {
                            CardQuestion(card, Modifier.animateItem()) { open(QueueSheet.MapCard(card)) }
                        }
                    }
                    items(list, key = { it.id }) { item ->
                        QueueItem(
                            item = item,
                            ref = ref,
                            now = now,
                            selected = item.id in selection,
                            selecting = selecting,
                            modifier = Modifier.animateItem(),
                            onOpen = {
                                if (selecting) {
                                    vm.toggleSelect(item.id)
                                } else if (item.status == SmsStatus.PENDING) {
                                    nav.navigate(EntryKey(mode = EntryMode.SMS, smsItemId = item.id, fromAdd = true))
                                }
                            },
                            onSelect = { haptics.longPress(); vm.toggleSelect(item.id) },
                            onApprove = { vm.approve(item.id) },
                            onDeny = { vm.deny(listOf(item.id)) },
                            onRestore = { haptics.confirm(); vm.restore(listOf(item.id)) },
                            onAmount = { editAmount = item },
                        )
                    }
                    if (list.any { it.status == SmsStatus.PENDING && it.rateFromApi }) {
                        item(key = "fx_attribution") {
                            Text(
                                stringResource(R.string.fx_attribution),
                                Modifier.fillMaxWidth().clickable { openRatesAttribution(context) }.padding(horizontal = Space.xxl, vertical = Space.m),
                                style = NexoType.Meta, color = colors.textSecondary, textAlign = TextAlign.Center,
                            )
                        }
                    }
                    item(key = "privacy") {
                        Text(
                            stringResource(R.string.sms_privacy_line),
                            Modifier.fillMaxWidth().padding(horizontal = Space.xxl, vertical = Space.xl),
                            style = NexoType.Meta, color = colors.textTertiary, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        UndoBar(undo, vm::dismissUndo, Modifier.align(Alignment.BottomCenter))

        InlineBottomSheet(
            visible = sheetOpen,
            onDismiss = { sheetOpen = false },
            heightFraction = if (sheet is QueueSheet.Deny) 0.5f else 0.72f,
        ) {
            when (val s = sheet) {
                is QueueSheet.MapCard -> {
                    SheetTitle(
                        stringResource(R.string.sms_card_question, com.nexo.app.sms.parse.Banks.byName(s.card.bank)?.displayName.orEmpty(), s.card.last4),
                        stringResource(R.string.sms_card_hint),
                    )
                    AccountPickList(ref, null) { id ->
                        vm.mapCard(s.card, id)
                        sheetOpen = false
                    }
                }
                QueueSheet.Assign -> {
                    SheetTitle(stringResource(R.string.sms_assign_account))
                    AccountPickList(ref, null) { id ->
                        vm.assignAccount(id)
                        sheetOpen = false
                    }
                }
                is QueueSheet.Deny -> {
                    SheetTitle(stringResource(R.string.sms_deny_title), stringResource(R.string.sms_deny_hint))
                    DenyReasonList { reason ->
                        vm.deny(s.ids, reason)
                        sheetOpen = false
                    }
                }
                null -> Unit
            }
        }

        InlineBottomSheet(visible = feePrompt != null, onDismiss = { vm.answerFee(null) }, heightFraction = 0.6f) {
            shownPrompt[0]?.let { p -> FeePromptContent(p, ref) { vm.answerFee(it) } }
        }
    }

    editAmount?.let { item ->
        val foreign = item.certainty == Certainty.ESTIMATED || item.actualAmount != null
        val account = ref.accountCurrency(item.accountId)
        AmountSheet(
            title = if (foreign) stringResource(R.string.sms_actual_amount_title, account.isoCode ?: account.symbol) else stringResource(R.string.sms_amount_title),
            subtitle = if (foreign) stringResource(R.string.sms_actual_amount_sub) else null,
            initial = if (foreign) item.actualAmount ?: item.estimatedAmount else item.amount,
            decimals = if (foreign) account.decimals else 2,
            allowSign = false,
            validate = { (it ?: 0.0) > 0.0 },
            onConfirm = { v ->
                if (v != null && v > 0) vm.setAmount(item.id, v)
                editAmount = null
            },
            onDismiss = { editAmount = null },
        )
    }
}

@Composable
private fun QueueItem(
    item: SmsItemEntity,
    ref: com.nexo.app.domain.RefData,
    now: Long,
    selected: Boolean,
    selecting: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onRestore: () -> Unit,
    onAmount: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val pending = item.status == SmsStatus.PENDING
    val restorable = item.status == SmsStatus.DENIED || item.status == SmsStatus.EXPIRED
    val row = @Composable {
        SmsQueueRow(
            item = item,
            ref = ref,
            selected = selected,
            now = now,
            onClick = onOpen,
            onLongClick = if (pending) onSelect else null,
            onAmountClick = if (pending && !selecting) onAmount else null,
            trailing = if (restorable) {
                { TextButton(onClick = onRestore) { Text(stringResource(R.string.sms_restore), color = colors.text) } }
            } else null,
        )
    }
    if (!pending) {
        Box(modifier) { row() }
        return
    }
    SwipeActionRow(
        start = SwipeAction(Icons.Rounded.Check, stringResource(R.string.sms_approve), enabled = !item.needsDetails, onTrigger = onApprove),
        end = SwipeAction(Icons.Rounded.Block, stringResource(R.string.sms_deny), onTrigger = onDeny),
        modifier = modifier,
        enabled = !selecting,
    ) { row() }
}

@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit, onAssign: () -> Unit, onDeny: () -> Unit, onApprove: () -> Unit) {
    val colors = LocalNexoColors.current
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_close), onClose)
        Spacer(Modifier.width(Space.m))
        Text(count.toString(), style = MaterialTheme.typography.titleLarge, color = colors.text, modifier = Modifier.weight(1f))
        RoundIconButton(Icons.Rounded.AccountBalanceWallet, stringResource(R.string.sms_assign_account), onAssign, tint = colors.textSecondary, container = Color.Transparent)
        RoundIconButton(Icons.Rounded.Block, stringResource(R.string.sms_deny), onDeny, tint = colors.textSecondary, container = Color.Transparent)
        Spacer(Modifier.width(Space.xs))
        Button(
            onClick = onApprove,
            colors = ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
            contentPadding = PaddingValues(horizontal = Space.l),
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text(stringResource(R.string.sms_approve), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** NQ-030: the capture switch mirrored on the queue header. Opens the SMS settings. */
@Composable
private fun CapturePill(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.clip(NexoShapes.pill).background(colors.raised).clickable(onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .then(if (on) Modifier.background(colors.text) else Modifier.border(1.5.dp, colors.textTertiary, CircleShape)),
        )
        Spacer(Modifier.width(Space.s))
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (on) colors.text else colors.textSecondary)
    }
}

@Composable
private fun FilterChips(selected: QueueFilter, pendingCount: Int, attentionCount: Int, onSelect: (QueueFilter) -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Space.gutter, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        QueueFilter.entries.forEach { f ->
            val on = f == selected
            val container by animateColorAsState(if (on) colors.text else colors.raised, NexoMotion.standard(), label = "chip")
            val content by animateColorAsState(if (on) colors.background else colors.textSecondary, NexoMotion.standard(), label = "chipText")
            val count = when (f) {
                QueueFilter.ALL -> pendingCount
                QueueFilter.ATTENTION -> attentionCount
                else -> 0
            }
            Text(
                if (count > 0) "${stringResource(f.label)}  $count" else stringResource(f.label),
                Modifier.clip(NexoShapes.pill).background(container).clickable { onSelect(f) }.padding(horizontal = Space.m + Space.xxs, vertical = Space.s),
                style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1,
            )
        }
    }
}

/** NQ-032 card mapping, asked once per card at the top of a list. */
@Composable
fun CardQuestion(card: CardRef, modifier: Modifier, onChoose: () -> Unit) {
    val bank = com.nexo.app.sms.parse.Banks.byName(card.bank)?.displayName.orEmpty()
    SmsPromptCard(
        stringResource(R.string.sms_card_question, bank, card.last4),
        stringResource(R.string.sms_card_hint),
        stringResource(R.string.sms_choose_account),
        onChoose,
        modifier,
    )
}
