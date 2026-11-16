package com.nexo.app.ui.entry

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.Currency
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.NexoMenu
import com.nexo.app.ui.designsystem.component.NexoMenuItem
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.pressScale
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shakeOn
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.sms.fxEstimateNote
import com.nexo.app.ui.sms.openRatesAttribution
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** The note, the amount and every field pill, then the details: description, FX note and original text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryFields(
    state: EntryUiState,
    actions: EntryActions,
    dialogs: EntryDialogState,
    typeColor: Color,
    noteFocus: FocusRequester,
    shake: Int,
    hasCamera: Boolean,
    modifier: Modifier = Modifier,
) {
    val clock = LocalClock.current
    val s = state.form
    val ref = state.ref
    val device = state.device
    val suggestions = state.suggestions
    val context = LocalContext.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val txCurrency = ref.currency(s.currencyId)
    var noteFocused by remember { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(Space.l))
        // Note: the entry's title, set above the amount.
        NoteField(
            value = s.note,
            onValueChange = actions.setNote,
            focused = noteFocused,
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = Space.xl,
            ).focusRequester(noteFocus).onFocusChanged {
                noteFocused = it.isFocused
                if (it.isFocused) {
                    actions.closePanel()
                    actions.enterEdit()
                }
            },
            onDone = { focus.clearFocus() },
            onClear = { actions.setNote("") },
        )
        if (noteFocused && s.note.isNotEmpty()) {
            val q = s.note
            val matches = remember(q, suggestions) {
                suggestions.mapNotNull { sug ->
                    sug.note.indexOf(q, ignoreCase = true).takeIf {
                        it >=
                            0 &&
                            sug.note != q
                    }?.let { sug to it }
                }
                    .sortedWith(
                        compareByDescending<Pair<NoteSuggestion, Int>> {
                            it.first.count
                        }.thenBy { it.second }.thenByDescending { it.first.lastDate },
                    )
                    .take(6)
            }
            if (matches.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(
                        horizontal = Space.gutter,
                        vertical = Space.xs,
                    ).clip(NexoShapes.control).background(colors.raised),
                ) {
                    matches.forEach { (sug, idx) ->
                        Text(
                            buildAnnotatedString {
                                append(sug.note.substring(0, idx))
                                withStyle(
                                    SpanStyle(
                                        color = typeColor,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                ) {
                                    append(sug.note.substring(idx, idx + q.length))
                                }
                                append(sug.note.substring(idx + q.length))
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            modifier = Modifier.fillMaxWidth().clickable {
                                actions.setNote(sug.note)
                                focus.clearFocus()
                            }.padding(horizontal = Space.l, vertical = Space.m),
                        )
                    }
                }
            }
        }

        // The amount.
        AmountDisplay(
            s = s,
            currency = txCurrency,
            color = typeColor,
            shake = shake,
            onClick = {
                haptics.tick()
                actions.openPanel(Panel.AMOUNT)
            },
        )
        val amount = s.amount
        if (txCurrency.id != ref.mainCurrency.id && amount != null && amount != 0.0 &&
            s.repeat == RepeatType.NONE
        ) {
            val rate = s.rate ?: Conversion.effectiveRate(txCurrency)
            val main = Money.round(Money.multiply(amount, rate), ref.mainCurrency.decimals)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "≈ " + AmountFormatter.format(main, ref.mainCurrency),
                    Modifier.clip(CircleShape).clickable {
                        dialogs.rateEdit = 1
                    }.padding(horizontal = Space.s, vertical = Space.xs),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("·", color = colors.textTertiary)
                Text(
                    stringResource(
                        R.string.exchange_rate_value,
                        AmountFormatter.rate(rate),
                    ),
                    Modifier.clip(CircleShape).clickable {
                        dialogs.rateEdit = 2
                    }.padding(horizontal = Space.s, vertical = Space.xs),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        s.installmentInfo?.let { info ->
            Text(
                stringResource(R.string.installment_k_n, info.index, info.count) + " · " +
                    stringResource(
                        R.string.installment_balance_total,
                        AmountFormatter.format(info.balance, txCurrency),
                        AmountFormatter.format(info.total, txCurrency),
                    ),
                style = NexoType.Meta,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(
                    horizontal = Space.gutter,
                    vertical = Space.xs,
                ),
            )
        }

        Spacer(Modifier.height(Space.l))
        // Field pills, in the configured input order, then when and how often.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(
                Space.s,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            s.fields.filter { it != Field.AMOUNT && it != Field.FEE }.forEach { f ->
                when (f) {
                    Field.ACCOUNT, Field.FROM, Field.TO -> {
                        val id = if (f == Field.TO) s.toAccountId else s.accountId
                        val label = stringResource(
                            if (f == Field.ACCOUNT) {
                                R.string.account
                            } else if (f == Field.FROM) {
                                R.string.from
                            } else {
                                R.string.to
                            },
                        )
                        val panel = EntryForm.panelFor(f)
                        FieldPill(
                            text = id?.let { ref.accountById[it]?.name } ?: label,
                            label = label,
                            placeholder = id == null,
                            active = s.panel == panel,
                            accent = typeColor,
                            icon = Icons.Outlined.AccountBalanceWallet,
                            prefix = if (s.isTransfer && id != null) label else null,
                        ) { actions.openPanel(panel) }
                        if (s.isTransfer &&
                            f == s.fields.firstOrNull {
                                it == Field.FROM || it == Field.TO
                            }
                        ) {
                            RoundIconButton(
                                Icons.Rounded.SwapHoriz,
                                stringResource(R.string.swap),
                                {
                                    haptics.tick()
                                    actions.swap()
                                },
                                size = 40.dp,
                                tint = colors.textSecondary,
                            )
                        }
                    }

                    Field.CATEGORY -> {
                        val visual = s.categoryId?.let { ref.visual(it) }
                        val text = categoryText(ref, s)
                        FieldPill(
                            text = text.ifEmpty { stringResource(R.string.category) },
                            label = stringResource(R.string.category),
                            placeholder = text.isEmpty(),
                            active = s.panel == Panel.CATEGORY,
                            accent = typeColor,
                            leading = visual?.let { v ->
                                {
                                    CategoryTile(
                                        v.emoji,
                                        v.monogram,
                                        CategoryHues.of(v.colorIndex, colors.isDark),
                                        size = 26.dp,
                                        over = colors.raised,
                                    )
                                }
                            },
                        ) { actions.openPanel(Panel.CATEGORY) }
                    }

                    else -> Unit
                }
            }
        }
        Spacer(Modifier.height(Space.s))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(
                Space.s,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            FieldPill(
                text = if (s.date.year == LocalDate.now(clock).year) {
                    Formats.weekdayMonthDay(s.date)
                } else {
                    Formats.mediumDate(s.date)
                },
                label = stringResource(R.string.date),
                icon = Icons.Outlined.CalendarToday,
                accent = typeColor,
            ) {
                actions.enterEdit()
                dialogs.date = true
            }
            if (ref.settings.timeDisplay.timeInput && s.showTime) {
                FieldPill(
                    text = Formats.time(s.time, DateFormat.is24HourFormat(context)),
                    label = stringResource(R.string.date),
                    icon = Icons.Outlined.Schedule,
                    accent = typeColor,
                ) {
                    actions.enterEdit()
                    dialogs.time = true
                }
            }
            if (s.showRepInst) {
                Box {
                    val anySet = s.repeat != RepeatType.NONE || s.installments > 0
                    FieldPill(
                        text = when {
                            s.repeat != RepeatType.NONE -> repeatLabel(s.repeat)

                            s.installments > 0 -> pluralStringResource(
                                R.plurals.n_months,
                                s.installments,
                                s.installments,
                            )

                            else -> stringResource(R.string.rep_inst)
                        },
                        label = stringResource(R.string.rep_inst),
                        placeholder = !anySet,
                        icon = Icons.Rounded.Repeat,
                        accent = typeColor,
                        tint = if (anySet) typeColor else null,
                    ) {
                        when {
                            s.repeatMode -> dialogs.repeatList = true
                            s.type == EntryType.EXPENSE -> dialogs.repeatMenu = true
                            !anySet -> dialogs.repeatList = true
                            else -> dialogs.repeatMenu = true
                        }
                    }
                    NexoMenu(dialogs.repeatMenu, onDismissRequest = {
                        dialogs.repeatMenu =
                            false
                    }) {
                        if (s.type == EntryType.EXPENSE && !s.isBalance) {
                            NexoMenuItem(stringResource(R.string.installment), {
                                dialogs.repeatMenu = false
                                dialogs.installments = true
                            }, Icons.Rounded.DateRange)
                        }
                        NexoMenuItem(stringResource(R.string.repeat), {
                            dialogs.repeatMenu = false
                            dialogs.repeatList = true
                        }, Icons.Rounded.Repeat)
                        if (s.repeat != RepeatType.NONE || s.installments > 0) {
                            NexoMenuItem(stringResource(R.string.action_cancel), {
                                dialogs.repeatMenu = false
                                actions.clearRepInst()
                            }, Icons.Rounded.Close, danger = true)
                        }
                    }
                }
            }
            if (s.repeat != RepeatType.NONE) {
                FieldPill(
                    text =
                        s.repeatEnd?.let {
                            stringResource(R.string.repeat_end) + " " +
                                Formats.shortDate(it)
                        }
                            ?: stringResource(R.string.repeat_no_end),
                    label = stringResource(R.string.repeat_end),
                    accent = typeColor,
                    onClear = if (s.repeatEnd != null) {
                        ({ actions.setRepeatEnd(null) })
                    } else {
                        null
                    },
                ) { dialogs.repeatEnd = true }
            }
            // Transfer fee, or a bank fee on an SMS expense.
            if (s.feeAllowed && s.feeExpanded) {
                val feeCur = ref.currency(s.feeCurrencyId)
                val text = if (s.panel == Panel.FEE && s.pad.buffer.isNotEmpty()) {
                    bufferParts(s.pad.buffer, feeCur).text
                } else {
                    s.feeAmount?.let { AmountFormatter.format(it, feeCur) } ?: ""
                }
                FieldPill(
                    text =
                        stringResource(R.string.fees) +
                            (if (text.isNotEmpty()) " · $text" else ""),
                    label = stringResource(R.string.fees),
                    active = s.panel == Panel.FEE,
                    accent = typeColor,
                    onClear = { actions.setFeeExpanded(false) },
                ) { actions.openPanel(Panel.FEE) }
            } else if (s.feeAllowed && s.repeat == RepeatType.NONE) {
                FieldPill(
                    text = stringResource(R.string.fees),
                    label = stringResource(R.string.fees),
                    placeholder = true,
                    icon = Icons.Rounded.Add,
                    accent = typeColor,
                ) {
                    actions.setFeeExpanded(true)
                }
            }
        }

        // Details: tags and Scan receipt as pills, then the description and any photos already attached.
        // Same gap and side padding as the rows above, so all the pill rows read as one evenly spaced block.
        Spacer(Modifier.height(Space.s))
        val photosAllowed = !s.repeatMode && s.repeat == RepeatType.NONE
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Space.l),
            horizontalArrangement = Arrangement.spacedBy(
                Space.s,
                Alignment.CenterHorizontally,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            if (s.repeat == RepeatType.NONE) {
                s.tagIds.mapNotNull { id ->
                    ref.tagById[id]?.takeIf { !it.isDeleted }
                }.forEach { t ->
                    FieldPill(
                        text = "#" + t.name,
                        label = stringResource(R.string.tags),
                        accent = typeColor,
                        onClear = {
                            actions.removeTag(t.id)
                        },
                    ) {
                        actions.enterEdit()
                        dialogs.tags = true
                    }
                }
                FieldPill(
                    text = stringResource(R.string.tags),
                    label = stringResource(R.string.tags),
                    placeholder = true,
                    icon = Icons.Rounded.Add,
                    accent = typeColor,
                ) {
                    actions.enterEdit()
                    dialogs.tags = true
                }
            }
            if (photosAllowed && s.scanAllowed) {
                Box {
                    FieldPill(
                        text = stringResource(R.string.receipt_scan),
                        label = stringResource(R.string.receipt_scan),
                        placeholder = true,
                        icon = Icons.Outlined.DocumentScanner,
                        accent = typeColor,
                    ) {
                        actions.enterEdit()
                        if (device?.photoNoticeShown == false) {
                            dialogs.photoNotice = true
                        } else {
                            actions.scanReceipt()
                        }
                    }
                    NexoMenu(dialogs.scanMenu, onDismissRequest = {
                        dialogs.scanMenu = false
                    }) {
                        if (hasCamera) {
                            NexoMenuItem(stringResource(R.string.camera), {
                                dialogs.scanMenu = false
                                actions.takePhoto()
                            }, Icons.Rounded.PhotoCamera)
                        }
                        NexoMenuItem(stringResource(R.string.gallery), {
                            dialogs.scanMenu = false
                            actions.pickPhoto()
                        }, Icons.Rounded.PhotoLibrary)
                    }
                }
            }
        }
        if (photosAllowed && s.photos.isNotEmpty()) {
            Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.m)) {
                PhotoThumbs(s.photos, state.photoFiles, removable = !s.viewing, onOpen = {
                    dialogs.viewerIndex = it
                }, onRemove = actions.removePhoto, onLongPress = actions.enterEdit)
            }
        }
        TextField(
            value = s.description,
            onValueChange = actions.setDescription,
            placeholder = {
                Text(stringResource(R.string.description), color = colors.textTertiary)
            },
            leadingIcon = {
                Icon(
                    Icons.AutoMirrored.Outlined.Notes,
                    null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            },
            minLines = 1,
            colors = quietFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = Space.s,
                vertical = Space.xs,
            ).onFocusChanged {
                if (it.isFocused) {
                    actions.closePanel()
                    actions.enterEdit()
                }
            },
        )
        val fxText = s.fxNote?.let { fx ->
            fxEstimateNote(context, fx.item, ref)?.let { note ->
                if (fx.editHint) {
                    note + "\n" + stringResource(R.string.fx_edit_hint)
                } else {
                    note
                }
            }
        }
        fxText?.let { note ->
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Text(note, style = NexoType.Meta, color = colors.textSecondary)
                if (s.fxFromApi) {
                    Text(
                        stringResource(R.string.fx_attribution),
                        Modifier.clickable {
                            openRatesAttribution(context)
                        }.padding(top = Space.xs),
                        style = NexoType.Meta,
                        color = colors.textTertiary,
                    )
                }
            }
        }
        s.sourceText?.let { src ->
            Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                Text(
                    stringResource(R.string.original_text).uppercase(Locale.getDefault()),
                    style = NexoType.Caps,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    src,
                    Modifier.fillMaxWidth().clip(
                        NexoShapes.control,
                    ).background(colors.raised).padding(Space.m),
                    style = NexoType.Meta,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}

private fun categoryText(ref: RefData, s: EntryForm): String {
    if (s.isBalance || (s.isBalanceAdjustment && s.categoryId == null)) return "—"
    val id = s.categoryId ?: return ""
    val main = ref.mainCategoryId(id)?.let { ref.categoryName(it) } ?: return ""
    val sub = ref.subCategoryId(id)?.let { ref.categoryName(it) }
    return if (sub != null) "$main › $sub" else main
}

/** The keypad buffer as display parts: locale grouping, the typed decimals, the currency symbol. */
private fun bufferParts(buffer: String, currency: Currency): AmountParts {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val negative = buffer.startsWith("-")
    val body = buffer.removePrefix("-")
    val intPart = body.substringBefore('.')
    val grouped =
        intPart.toBigIntegerOrNull()?.let { NumberFormat.getIntegerInstance().format(it) }
            ?: intPart.ifEmpty { "0" }
    val fraction = if (body.contains('.')) separator + body.substringAfter('.') else ""
    val prefix = currency.symbolPosition == SymbolPosition.PREFIX
    return AmountParts(
        if (negative) AmountParts.MINUS else "",
        if (prefix) currency.symbol else "",
        grouped,
        fraction,
        if (prefix) "" else currency.symbol,
    )
}

/** "0.00" in the currency's shape, shown dimmed before anything is typed. */
private fun placeholderParts(currency: Currency): AmountParts {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val prefix = currency.symbolPosition == SymbolPosition.PREFIX
    val fraction = if (currency.decimals > 0) separator + "0".repeat(currency.decimals) else ""
    return AmountParts(
        "",
        if (prefix) currency.symbol else "",
        "0",
        fraction,
        if (prefix) "" else currency.symbol,
    )
}

/**
 * The amount as the screen's centrepiece. Each keystroke gives it a small punch; a rejected key
 * shakes it. While the keypad is open a short bar under it breathes in the entry's colour.
 */
@Composable
private fun AmountDisplay(
    s: EntryForm,
    currency: Currency,
    color: Color,
    shake: Int,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val typing = s.panel == Panel.AMOUNT && s.pad.buffer.isNotEmpty()
    val parts = when {
        typing -> bufferParts(s.pad.buffer, currency)
        s.amount != null -> AmountParts.of(s.amount, currency)
        else -> null
    }
    val punch = remember { Animatable(1f) }
    LaunchedEffect(s.pad.buffer) {
        if (s.panel == Panel.AMOUNT) {
            punch.snapTo(1.04f)
            punch.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 700f))
        }
    }
    val active = s.panel == Panel.AMOUNT
    val tint by animateColorAsState(
        if (parts == null) {
            colors.textTertiary
        } else {
            color
        },
        NexoMotion.standard(),
        label = "amountTint",
    )
    Column(
        Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
        )
            .padding(horizontal = Space.gutter, vertical = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.shakeOn(shake).graphicsLayer {
                scaleX = punch.value
                scaleY = punch.value
            },
            contentAlignment = Alignment.Center,
        ) {
            AmountText(
                parts ?: placeholderParts(currency),
                Modifier.shrinkToFit(),
                style = NexoType.Hero,
                color = tint,
                minorScale = 0.5f,
            )
        }
        AnimatedVisibility(
            active,
            enter = fadeIn() + expandVertically(),
            exit =
                fadeOut() + shrinkVertically(),
        ) {
            val pulse = rememberInfiniteTransition(label = "caret")
            val alpha by pulse.animateFloat(
                0.35f,
                1f,
                infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "caretAlpha",
            )
            Box(
                Modifier.padding(
                    top = Space.s,
                ).size(
                    width = 28.dp,
                    height = 3.dp,
                ).clip(CircleShape).background(color.copy(alpha = alpha)),
            )
        }
    }
}

/** The note, set as a centred title above the amount. */
@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    focused: Boolean,
    modifier: Modifier,
    onDone: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val style = TextStyle(
        fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = MaterialTheme.typography.titleLarge.fontSize,
        color = colors.text,
        textAlign = TextAlign.Center,
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(colors.text),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = Space.s),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.note),
                            style = style.copy(color = colors.textTertiary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    inner()
                }
            },
        )
        if (focused && value.isNotEmpty()) {
            RoundIconButton(
                Icons.Rounded.Close,
                stringResource(R.string.action_clear),
                onClear,
                modifier = Modifier.align(Alignment.CenterEnd),
                size = 28.dp,
                tint = colors.textSecondary,
            )
        }
    }
}

/**
 * A field as a pill: icon or tile, value (or its name, dimmed, when empty). The active field is
 * ringed in the entry's colour. [onClear] adds a small remove control.
 */
@Composable
private fun FieldPill(
    text: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    active: Boolean = false,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    prefix: String? = null,
    tint: Color? = null,
    onClear: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val interactions = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (active) colors.tint(accent, colors.raised) else colors.raised,
        NexoMotion.standard(),
        label = "pillBg",
    )
    val ring by animateColorAsState(
        if (active) accent else Color.Transparent,
        NexoMotion.standard(),
        label = "pillRing",
    )
    Row(
        modifier
            .pressScale(interactions, 0.96f)
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, ring, CircleShape)
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = if (placeholder) label else "$label: $text" }
            .padding(
                start = if (leading != null || icon != null) {
                    Space.s
                } else {
                    Space.m + Space.xxs
                },
                end = if (onClear != null) {
                    Space.xs
                } else {
                    Space.m + Space.xxs
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(Modifier.width(Space.s))
            }

            icon != null -> {
                Icon(
                    icon,
                    null,
                    tint = tint ?: colors.textSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Space.s - Space.xxs))
            }
        }
        if (prefix != null) {
            Text(prefix, style = MaterialTheme.typography.labelMedium, color = colors.textTertiary)
            Spacer(Modifier.width(Space.xs))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                tint != null -> tint
                placeholder -> colors.textTertiary
                else -> colors.text
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp),
        )
        if (onClear != null) {
            Spacer(Modifier.width(Space.xxs))
            RoundIconButton(
                Icons.Rounded.Close,
                stringResource(R.string.action_clear),
                onClear,
                size = 28.dp,
                tint = colors.textTertiary,
                container = Color.Transparent,
            )
        }
    }
}

@Composable
private fun quietFieldColors() = LocalNexoColors.current.let { c ->
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = c.hairline,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = c.text,
    )
}
