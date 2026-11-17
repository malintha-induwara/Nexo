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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Banks
import com.nexo.app.core.model.Certainty
import com.nexo.app.core.model.DenyReason
import com.nexo.app.core.model.FeeChoice
import com.nexo.app.core.model.RateChoice
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.SmsItem
import com.nexo.app.core.model.SmsKind
import com.nexo.app.core.model.SmsStatus
import com.nexo.app.core.model.needsAttention
import com.nexo.app.core.model.needsDetails
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.InlineBottomSheet
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.SwipeAction
import com.nexo.app.ui.designsystem.component.SwipeActionRow
import com.nexo.app.ui.designsystem.component.UndoBar
import com.nexo.app.ui.designsystem.component.UndoMessage
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsHistoryKey
import com.nexo.app.ui.navigation.SmsSettingsKey
import kotlinx.coroutines.delay

private sealed interface QueueSheet {
    data class MapCard(val card: CardRef) : QueueSheet
    data object Assign : QueueSheet
    data class Deny(val ids: Set<String>) : QueueSheet
}

class SmsQueueActions(
    val onBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenItem: (itemId: String) -> Unit = {},
    val onReviewGap: (gapFrom: Long, gapUntil: Long) -> Unit = { _, _ -> },
    val onChooseHistory: () -> Unit = {},
    val onOpenRatesAttribution: () -> Unit = {},
    val onSetFilter: (QueueFilter) -> Unit = {},
    val onToggleSelect: (itemId: String) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    val onApproveSelected: () -> Unit = {},
    val onAssignAccount: (accountId: String) -> Unit = {},
    val onApprove: (itemId: String) -> Unit = {},
    val onDeny: (ids: Collection<String>, reason: DenyReason?) -> Unit = { _, _ -> },
    val onRestore: (ids: Collection<String>) -> Unit = {},
    val onSetAmount: (itemId: String, value: Double) -> Unit = { _, _ -> },
    val onMapCard: (CardRef, accountId: String) -> Unit = { _, _ -> },
    val onClearGap: () -> Unit = {},
    val onSkipHistory: () -> Unit = {},
    val onAnswerFee: (FeeChoice?) -> Unit = {},
    val onUndo: (QueueNotice) -> Unit = {},
    val onDismissNotice: () -> Unit = {},
)

@Composable
fun SmsQueueRoute(vm: SmsQueueViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        vm.markSeen()
        onPauseOrDispose {}
    }
    val actions = remember(vm, nav, context) {
        SmsQueueActions(
            onBack = nav::back,
            onOpenSettings = { nav.navigate(SmsSettingsKey) },
            onOpenItem = { id ->
                nav.navigate(EntryKey(mode = EntryMode.SMS, smsItemId = id, fromAdd = true))
            },
            onReviewGap = { from, until -> nav.navigate(SmsHistoryKey(from, until)) },
            onChooseHistory = { nav.navigate(SmsHistoryKey()) },
            onOpenRatesAttribution = { openRatesAttribution(context) },
            onSetFilter = vm::setFilter,
            onToggleSelect = vm::toggleSelect,
            onClearSelection = vm::clearSelection,
            onApproveSelected = vm::approveSelected,
            onAssignAccount = vm::assignAccount,
            onApprove = vm::approve,
            onDeny = vm::deny,
            onRestore = vm::restore,
            onSetAmount = vm::setAmount,
            onMapCard = vm::mapCard,
            onClearGap = vm::clearGap,
            onSkipHistory = vm::skipHistory,
            onAnswerFee = vm::answerFee,
            onUndo = vm::undo,
            onDismissNotice = vm::dismissNotice,
        )
    }
    SmsQueueScreen(state, actions)
}

@Composable
internal fun SmsQueueScreen(state: SmsQueueUiState, actions: SmsQueueActions) {
    val clock = LocalClock.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val ref = state.ref
    val device = state.device
    val filter = state.filter
    val items = state.items
    val pending = state.pending
    val unmapped = state.unmappedCards
    val selection = state.selection
    val permitted = state.permitted
    val feePrompt = state.feePrompt
    val undoLabel = stringResource(R.string.sms_undo)
    val undo = state.notice?.let { notice ->
        val text = notice.text.asString()
        remember(notice.id) {
            UndoMessage(text, undoLabel.takeIf { notice.undo != null }) { actions.onUndo(notice) }
        }
    }
    // Keeps the last question drawn while the sheet slides away.
    val shownPrompt = remember { arrayOfNulls<FeePrompt>(1) }
    if (feePrompt != null) shownPrompt[0] = feePrompt

    var sheet by remember { mutableStateOf<QueueSheet?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var editAmount by remember { mutableStateOf<SmsItem?>(null) }
    var now by remember { mutableLongStateOf(clock.millis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = clock.millis()
        }
    }
    BackHandler(selection.isNotEmpty()) { actions.onClearSelection() }

    fun open(s: QueueSheet) {
        sheet = s
        sheetOpen = true
    }
    val selecting = selection.isNotEmpty()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedContent(selecting, transitionSpec = {
                fadeIn(NexoMotion.enter()) togetherWith
                    fadeOut(NexoMotion.exit())
            }, label = "queueHeader") { sel ->
                if (sel) {
                    SelectionHeader(
                        count = selection.size,
                        onClose = actions.onClearSelection,
                        onAssign = { open(QueueSheet.Assign) },
                        onDeny = { open(QueueSheet.Deny(selection)) },
                        onApprove = {
                            haptics.confirm()
                            actions.onApproveSelected()
                        },
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(
                            56.dp,
                        ).padding(start = Space.m, end = Space.gutter),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundIconButton(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            stringResource(R.string.action_back),
                            actions.onBack,
                        )
                        Spacer(Modifier.width(Space.m))
                        Text(
                            stringResource(R.string.sms_queue_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                        CapturePill(
                            label = when {
                                device?.smsCaptureEnabled == false -> stringResource(
                                    R.string.sms_capture_off,
                                )

                                !permitted -> stringResource(R.string.sms_capture_needs_permission)

                                else -> stringResource(R.string.sms_capture_on)
                            },
                            on = device?.smsCaptureEnabled != false && permitted,
                        ) { actions.onOpenSettings() }
                    }
                }
            }
            FilterChips(
                selected = filter,
                pendingCount = pending.size,
                attentionCount = pending.count { it.needsAttention },
                onSelect = {
                    haptics.tick()
                    actions.onSetFilter(it)
                },
            )
            val list = items
            val queueView = filter.status == SmsStatus.PENDING && !selecting
            val showCards = queueView && unmapped.isNotEmpty()
            val gapFrom = device?.smsGapFrom ?: 0L
            val gapUntil = device?.smsGapUntil ?: 0L
            val showGap = queueView && gapFrom > 0
            val showOffer = queueView && !showGap && permitted &&
                device?.smsHistoryImport.let {
                    it == SmsHistoryImport.NOT_OFFERED ||
                        it == SmsHistoryImport.OFFERED
                }
            when {
                list == null -> Unit

                list.isEmpty() && !showCards && !showGap && !showOffer -> EmptyState(
                    stringResource(
                        if (filter == QueueFilter.ALL) {
                            R.string.sms_empty
                        } else {
                            R.string.sms_empty_filter
                        },
                    ),
                    Modifier.padding(top = Space.xxxl),
                )

                else -> LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    if (showGap) {
                        item(key = "gap") {
                            SmsPromptCard(
                                stringResource(R.string.sms_gap_hint),
                                null,
                                stringResource(R.string.sms_history_title),
                                { actions.onReviewGap(gapFrom, gapUntil) },
                                Modifier.animateItem(),
                                stringResource(R.string.sms_history_not_now),
                                actions.onClearGap,
                            )
                        }
                    }
                    if (showOffer) {
                        item(key = "offer") {
                            SmsPromptCard(
                                stringResource(R.string.sms_history_offer_title),
                                stringResource(R.string.sms_history_offer_text),
                                stringResource(R.string.sms_history_choose),
                                actions.onChooseHistory,
                                Modifier.animateItem(),
                                stringResource(R.string.sms_history_not_now),
                                actions.onSkipHistory,
                            )
                        }
                    }
                    if (showCards) {
                        val card = unmapped.first()
                        item(key = "card_${card.bank}_${card.last4}") {
                            CardQuestion(card, Modifier.animateItem()) {
                                open(QueueSheet.MapCard(card))
                            }
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
                                    actions.onToggleSelect(item.id)
                                } else if (item.status == SmsStatus.PENDING) {
                                    actions.onOpenItem(item.id)
                                }
                            },
                            onSelect = {
                                haptics.longPress()
                                actions.onToggleSelect(item.id)
                            },
                            onApprove = { actions.onApprove(item.id) },
                            onDeny = { actions.onDeny(listOf(item.id), null) },
                            onRestore = {
                                haptics.confirm()
                                actions.onRestore(listOf(item.id))
                            },
                            onAmount = { editAmount = item },
                        )
                    }
                    if (list.any { it.status == SmsStatus.PENDING && it.rateFromApi }) {
                        item(key = "fx_attribution") {
                            Text(
                                stringResource(R.string.fx_attribution),
                                Modifier.fillMaxWidth().clickable(
                                    onClick = actions.onOpenRatesAttribution,
                                ).padding(horizontal = Space.xxl, vertical = Space.m),
                                style = NexoType.Meta,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    item(key = "privacy") {
                        Text(
                            stringResource(R.string.sms_privacy_line),
                            Modifier.fillMaxWidth().padding(
                                horizontal = Space.xxl,
                                vertical = Space.xl,
                            ),
                            style = NexoType.Meta,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        UndoBar(undo, actions.onDismissNotice, Modifier.align(Alignment.BottomCenter))

        InlineBottomSheet(
            visible = sheetOpen,
            onDismiss = { sheetOpen = false },
            heightFraction = if (sheet is QueueSheet.Deny) 0.5f else 0.72f,
        ) {
            when (val s = sheet) {
                is QueueSheet.MapCard -> {
                    SheetTitle(
                        stringResource(
                            R.string.sms_card_question,
                            Banks.byName(s.card.bank)?.displayName.orEmpty(),
                            s.card.last4,
                        ),
                        stringResource(R.string.sms_card_hint),
                    )
                    AccountPickList(ref, null) { id ->
                        actions.onMapCard(s.card, id)
                        sheetOpen = false
                    }
                }

                QueueSheet.Assign -> {
                    SheetTitle(stringResource(R.string.sms_assign_account))
                    AccountPickList(ref, null) { id ->
                        actions.onAssignAccount(id)
                        sheetOpen = false
                    }
                }

                is QueueSheet.Deny -> {
                    SheetTitle(
                        stringResource(R.string.sms_deny_title),
                        stringResource(R.string.sms_deny_hint),
                    )
                    DenyReasonList { reason ->
                        actions.onDeny(s.ids, reason)
                        sheetOpen = false
                    }
                }

                null -> Unit
            }
        }

        InlineBottomSheet(visible = feePrompt != null, onDismiss = {
            actions.onAnswerFee(null)
        }, heightFraction = 0.6f) {
            shownPrompt[0]?.let { p -> FeePromptContent(p, ref, actions.onAnswerFee) }
        }
    }

    editAmount?.let { item ->
        val foreign = item.certainty == Certainty.ESTIMATED || item.actualAmount != null
        val account = ref.accountCurrency(item.accountId)
        AmountSheet(
            title = if (foreign) {
                stringResource(R.string.sms_actual_amount_title, account.isoCode ?: account.symbol)
            } else {
                stringResource(R.string.sms_amount_title)
            },
            subtitle = if (foreign) stringResource(R.string.sms_actual_amount_sub) else null,
            initial = if (foreign) item.actualAmount ?: item.estimatedAmount else item.amount,
            decimals = if (foreign) account.decimals else 2,
            allowSign = false,
            validate = { (it ?: 0.0) > 0.0 },
            onConfirm = { v ->
                if (v != null && v > 0) actions.onSetAmount(item.id, v)
                editAmount = null
            },
            onDismiss = { editAmount = null },
        )
    }
}

@Composable
private fun QueueItem(
    item: SmsItem,
    ref: RefData,
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
                {
                    TextButton(onClick = onRestore) {
                        Text(stringResource(R.string.sms_restore), color = colors.text)
                    }
                }
            } else {
                null
            },
        )
    }
    if (!pending) {
        Box(modifier) { row() }
        return
    }
    SwipeActionRow(
        start = SwipeAction(
            Icons.Rounded.Check,
            stringResource(R.string.sms_approve),
            enabled = !item.needsDetails,
            onTrigger = onApprove,
        ),
        end = SwipeAction(
            Icons.Rounded.Block,
            stringResource(R.string.sms_deny),
            onTrigger = onDeny,
        ),
        modifier = modifier,
        enabled = !selecting,
    ) { row() }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onAssign: () -> Unit,
    onDeny: () -> Unit,
    onApprove: () -> Unit,
) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_close), onClose)
        Spacer(Modifier.width(Space.m))
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(
            Icons.Rounded.AccountBalanceWallet,
            stringResource(R.string.sms_assign_account),
            onAssign,
            tint = colors.textSecondary,
            container = Color.Transparent,
        )
        RoundIconButton(
            Icons.Rounded.Block,
            stringResource(R.string.sms_deny),
            onDeny,
            tint = colors.textSecondary,
            container = Color.Transparent,
        )
        Spacer(Modifier.width(Space.xs))
        Button(
            onClick = onApprove,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
            contentPadding = PaddingValues(horizontal = Space.l),
        ) {
            Icon(Icons.Rounded.DoneAll, null, Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
            Text(stringResource(R.string.sms_approve), style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Mirrors the capture switch on the queue header; tapping opens the SMS settings.
@Composable
private fun CapturePill(label: String, on: Boolean, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.clip(
            NexoShapes.pill,
        ).background(
            colors.raised,
        ).clickable(onClick = onClick).padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .then(
                    if (on) {
                        Modifier.background(colors.primary)
                    } else {
                        Modifier.border(1.5.dp, colors.textTertiary, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(Space.s))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (on) colors.text else colors.textSecondary,
        )
    }
}

@Composable
private fun FilterChips(
    selected: QueueFilter,
    pendingCount: Int,
    attentionCount: Int,
    onSelect: (QueueFilter) -> Unit,
) {
    val colors = LocalNexoColors.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(
            rememberScrollState(),
        ).padding(horizontal = Space.gutter, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        QueueFilter.entries.forEach { f ->
            val on = f == selected
            val container by animateColorAsState(
                if (on) colors.primarySoft else colors.raised,
                NexoMotion.standard(),
                label = "chip",
            )
            val content by animateColorAsState(
                if (on) colors.primaryText else colors.textSecondary,
                NexoMotion.standard(),
                label = "chipText",
            )
            val count = when (f) {
                QueueFilter.ALL -> pendingCount
                QueueFilter.ATTENTION -> attentionCount
                else -> 0
            }
            Text(
                if (count > 0) "${stringResource(f.label)}  $count" else stringResource(f.label),
                Modifier.clip(NexoShapes.pill).background(container).clickable {
                    onSelect(f)
                }.padding(horizontal = Space.m + Space.xxs, vertical = Space.s),
                style = MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
            )
        }
    }
}

// Asked once per card, at the top of a list.
@Composable
fun CardQuestion(card: CardRef, modifier: Modifier, onChoose: () -> Unit) {
    val bank = Banks.byName(card.bank)?.displayName.orEmpty()
    SmsPromptCard(
        stringResource(R.string.sms_card_question, bank, card.last4),
        stringResource(R.string.sms_card_hint),
        stringResource(R.string.sms_choose_account),
        onChoose,
        modifier,
    )
}

private val previewNow = PreviewData.clock.millis()

private fun previewItem(
    id: String,
    kind: SmsKind,
    bank: String,
    amount: Double,
    hoursAgo: Long,
    merchant: String = "",
    currency: String = "LKR",
    cardLast4: String? = null,
    accountId: String? = null,
    categoryId: String? = null,
) = (previewNow - hoursAgo * 3_600_000).let { at ->
    SmsItem(
        id = id,
        certainty = Certainty.EXACT,
        kind = kind,
        bank = bank,
        sender = bank,
        template = "purchase",
        amount = amount,
        currency = currency,
        merchant = merchant,
        cardLast4 = cardLast4,
        occurredAt = at,
        smsAt = at,
        capturedAt = at,
        dedupeKey = id,
        text = "Sample message",
        accountId = accountId,
        categoryId = categoryId,
        updatedAt = at,
    )
}

// Made-up items for previews and tests.
internal val previewSmsItems = listOf(
    previewItem(
        "grocery",
        SmsKind.EXPENSE,
        "COMBANK",
        2_450.0,
        hoursAgo = 2,
        merchant = "Corner Grocer",
        cardLast4 = "1234",
        accountId = "card",
        categoryId = "groceries",
    ),
    previewItem(
        "streaming",
        SmsKind.EXPENSE,
        "COMBANK",
        12.99,
        hoursAgo = 20,
        merchant = "Sample Streaming",
        currency = "USD",
        cardLast4 = "1234",
        accountId = "card",
    ).copy(
        certainty = Certainty.ESTIMATED,
        estimatedAmount = 3_897.0,
        fxRate = 300.0,
        fxSource = RateChoice.MEDIAN,
        fxSnapshotAt = previewNow,
    ),
    previewItem(
        "atm",
        SmsKind.TRANSFER_TO_CASH,
        "BOC",
        10_000.0,
        hoursAgo = 30,
        cardLast4 = "5678",
    ),
)

internal val previewSmsQueueState = SmsQueueUiState(
    ref = PreviewData.ref,
    device = PreviewData.device,
    pending = previewSmsItems,
    items = previewSmsItems,
    unmappedCards = listOf(CardRef("BOC", "5678")),
    permitted = true,
)

@Preview
@Composable
private fun SmsQueueScreenPreview() = NexoPreview {
    SmsQueueScreen(previewSmsQueueState, SmsQueueActions())
}

@Preview
@Composable
private fun SmsQueueScreenSelectingPreview() = NexoPreview(dark = true) {
    SmsQueueScreen(
        previewSmsQueueState.copy(selection = setOf("grocery", "streaming")),
        SmsQueueActions(),
    )
}
