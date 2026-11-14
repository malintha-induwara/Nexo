package com.nexo.app.ui.entry

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.Undo
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
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RefData
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.core.model.TxKind
import com.nexo.app.data.database.entity.CurrencyEntity
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.CalculatorDialog
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.component.InlineBottomSheet
import com.nexo.app.ui.designsystem.component.NexoDatePickerDialog
import com.nexo.app.ui.designsystem.component.NexoMenu
import com.nexo.app.ui.designsystem.component.NexoMenuItem
import com.nexo.app.ui.designsystem.component.NexoTimePickerDialog
import com.nexo.app.ui.designsystem.component.NumberPad
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.SegmentedPill
import com.nexo.app.ui.designsystem.component.pressScale
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.component.shakeOn
import com.nexo.app.ui.designsystem.component.shrinkToFit
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.lock.expectReturn
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SubCurrenciesKey
import java.io.File
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(key: EntryKey) {
    val vm = hiltViewModel<EntryViewModel, EntryViewModel.Factory>(creationCallback = { it.create(key) })
    val s by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val tags by vm.tagRepo.getTagsStream().collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val noteFocus = remember { FocusRequester() }

    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showCalc by rememberSaveable { mutableStateOf(false) }
    var showRepeatList by rememberSaveable { mutableStateOf(false) }
    var showRepMenu by rememberSaveable { mutableStateOf(false) }
    var showInstallments by rememberSaveable { mutableStateOf(false) }
    var showRepeatEnd by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showCopy by rememberSaveable { mutableStateOf(false) }
    var showPhotoNotice by rememberSaveable { mutableStateOf(false) }
    var showScanMenu by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
    var rateEdit by rememberSaveable { mutableIntStateOf(0) }
    var showTransferGuide by rememberSaveable { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
    var shake by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is EntryEvent.Message -> {
                    haptics.reject()
                    Toast.makeText(
                        context,
                        if (e.arg != null) resources.getString(e.text, e.arg) else resources.getString(e.text),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is EntryEvent.Close -> {
                    e.date?.let(vm::focus)
                    nav.back()
                }
                is EntryEvent.Open -> if (e.replace) nav.replace(e.key) else nav.navigate(e.key)
                EntryEvent.FocusNote -> noteFocus.requestFocus()
            }
        }
    }
    LaunchedEffect(s.noteFocusRequest) {
        if (s.noteFocusRequest > 0) {
            runCatching { noteFocus.requestFocus() }
            keyboard?.show()
        }
    }
    LaunchedEffect(s.panel) {
        if (s.panel != Panel.NONE) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    BackHandler(enabled = s.panel != Panel.NONE) { vm.closePanel() }

    val typeColor = when {
        s.type == EntryType.INCOME -> money.income
        s.type == EntryType.EXPENSE -> money.expense
        else -> colors.text
    }
    val txCurrency = ref.currency(s.currencyId)
    val modeLabel = when {
        s.balanceKind == TxKind.BALANCE_INCREASE -> stringResource(R.string.income_balance)
        s.balanceKind == TxKind.BALANCE_DECREASE -> stringResource(R.string.expense_balance)
        s.mode == EntryMode.PAY_CARD -> stringResource(R.string.card_payment)
        s.repeatMode -> stringResource(R.string.repeat)
        else -> null
    }

    // Scan receipt: the document scanner (rear camera, edges found and cropped, or a photo from the gallery) hands back
    // one page, which is read and then attached once the user confirms it. Where the scanner cannot start (no Google Play
    // services, or a phone it does not support) the plain camera and gallery choices take its place.
    val scanDocument = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri?.let(vm::scanReceipt)
        }
    }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val scanCamera = rememberLauncherForActivityResult(RearCameraPicture()) { ok ->
        if (ok) cameraUri?.let { vm.scanReceipt(Uri.parse(it)) }
    }
    val scanGallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (vm.photoStore.isSupported(uri)) vm.scanReceipt(uri) else Toast.makeText(context, R.string.unsupported_file_format, Toast.LENGTH_SHORT).show()
        }
    }
    fun launchScanner() {
        val activity = context.findActivity()
        if (activity == null) {
            showScanMenu = true
            return
        }
        receiptScanner().getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                context.expectReturn()
                runCatching { scanDocument.launch(IntentSenderRequest.Builder(sender).build()) }.onFailure { showScanMenu = true }
            }
            .addOnFailureListener { showScanMenu = true }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "temp").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri = uri.toString()
        context.expectReturn()
        scanCamera.launch(uri)
    }

    /** Keypad input with a shake and a reject buzz when the key cannot change the amount. */
    fun pressKey(k: PadKey) {
        val r = s.pad.press(k)
        if (r.input == s.pad && !r.negateExisting && !r.clearField) {
            shake++
            haptics.reject()
        }
        vm.padKey(k)
    }

    // An edited entry loads from the database a moment after the screen appears; fade it in rather than pop.
    val reveal by animateFloatAsState(if (s.loaded) 1f else 0f, tween(180), label = "entryReveal")
    // As a drawer (opened from an add button) the sheet supplies the background and already sits below the status bar.
    Box(if (key.fromAdd) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        if (!s.loaded) return@Box
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.imePadding()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown || (s.panel != Panel.AMOUNT && s.panel != Panel.FEE)) return@onPreviewKeyEvent false
                    val k = when (ev.key) {
                        Key.Zero, Key.NumPad0 -> PadKey.D0
                        Key.One, Key.NumPad1 -> PadKey.D1
                        Key.Two, Key.NumPad2 -> PadKey.D2
                        Key.Three, Key.NumPad3 -> PadKey.D3
                        Key.Four, Key.NumPad4 -> PadKey.D4
                        Key.Five, Key.NumPad5 -> PadKey.D5
                        Key.Six, Key.NumPad6 -> PadKey.D6
                        Key.Seven, Key.NumPad7 -> PadKey.D7
                        Key.Eight, Key.NumPad8 -> PadKey.D8
                        Key.Nine, Key.NumPad9 -> PadKey.D9
                        Key.Backspace -> PadKey.BACKSPACE
                        Key.Minus, Key.NumPadSubtract -> PadKey.SIGN
                        Key.Period, Key.Comma, Key.NumPadDot -> PadKey.DECIMAL
                        Key.Enter, Key.NumPadEnter -> { vm.padDone(); return@onPreviewKeyEvent true }
                        else -> return@onPreviewKeyEvent false
                    }
                    pressKey(k)
                    true
                },
        ) {
            // Top: close, type, bookmarks / revert.
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Space.m), verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_back), { nav.back() })
                Box(Modifier.weight(1f).padding(horizontal = Space.s), contentAlignment = Alignment.Center) {
                    SegmentedPill(
                        options = listOf(EntryType.INCOME, EntryType.EXPENSE, EntryType.TRANSFER),
                        selected = s.type,
                        onSelect = { t ->
                            if (t == EntryType.TRANSFER && device?.transferGuideShown == false) showTransferGuide = true
                            vm.setType(t)
                        },
                        label = { t ->
                            stringResource(
                                when (t) {
                                    EntryType.INCOME -> R.string.income
                                    EntryType.EXPENSE -> R.string.expense
                                    EntryType.TRANSFER -> R.string.transfer
                                },
                            )
                        },
                        accent = { t -> if (t == EntryType.INCOME) money.income else if (t == EntryType.EXPENSE) money.expense else null },
                        enabled = s.mode != EntryMode.PAY_CARD || s.isBalance,
                        showSelection = !s.isBalance,
                        segmentPadding = 12.dp,
                        modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                    )
                }
                Box(Modifier.size(40.dp)) {
                    when {
                        s.isEdit && !s.viewing -> RoundIconButton(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.revert), vm::revert)
                        (s.mode == EntryMode.NEW || s.mode == EntryMode.PAY_CARD || s.mode == EntryMode.FAVORITE) && s.originalId == null ->
                            RoundIconButton(Icons.Rounded.StarOutline, stringResource(R.string.bookmarks), { nav.navigate(BookmarksKey(pickForForm = true)) })
                    }
                }
            }
            modeLabel?.let {
                Text(
                    it.uppercase(Locale.getDefault()), style = NexoType.Caps, color = typeColor,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs),
                )
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(Space.l))
                // Note: the entry's title, set above the amount.
                NoteField(
                    value = s.note,
                    onValueChange = vm::setNote,
                    focused = noteFocused,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl).focusRequester(noteFocus).onFocusChanged {
                        noteFocused = it.isFocused
                        if (it.isFocused) { vm.closePanel(); vm.enterEdit() }
                    },
                    onDone = { focus.clearFocus() },
                    onClear = { vm.setNote("") },
                )
                if (noteFocused && s.note.isNotEmpty()) {
                    val q = s.note
                    val matches = remember(q, suggestions) {
                        suggestions.mapNotNull { sug -> sug.note.indexOf(q, ignoreCase = true).takeIf { it >= 0 && sug.note != q }?.let { sug to it } }
                            .sortedWith(compareByDescending<Pair<NoteSuggestion, Int>> { it.first.count }.thenBy { it.second }.thenByDescending { it.first.lastDate })
                            .take(6)
                    }
                    if (matches.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs).clip(NexoShapes.control).background(colors.raised)) {
                            matches.forEach { (sug, idx) ->
                                Text(
                                    buildAnnotatedString {
                                        append(sug.note.substring(0, idx))
                                        withStyle(SpanStyle(color = typeColor, fontWeight = FontWeight.SemiBold)) { append(sug.note.substring(idx, idx + q.length)) }
                                        append(sug.note.substring(idx + q.length))
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                    modifier = Modifier.fillMaxWidth().clickable { vm.setNote(sug.note); focus.clearFocus() }.padding(horizontal = Space.l, vertical = Space.m),
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
                    onClick = { haptics.tick(); vm.openPanel(Panel.AMOUNT) },
                )
                val amount = s.amount
                if (txCurrency.id != ref.mainCurrency.id && amount != null && amount != 0.0 && s.repeat == RepeatType.NONE) {
                    val rate = s.rate ?: Conversion.effectiveRate(txCurrency)
                    val main = Money.round(Money.multiply(amount, rate), ref.mainCurrency.decimals)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "≈ " + AmountFormatter.format(main, ref.mainCurrency),
                            Modifier.clip(CircleShape).clickable { rateEdit = 1 }.padding(horizontal = Space.s, vertical = Space.xs),
                            color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium,
                        )
                        Text("·", color = colors.textTertiary)
                        Text(
                            stringResource(R.string.exchange_rate_value, AmountFormatter.rate(rate)),
                            Modifier.clip(CircleShape).clickable { rateEdit = 2 }.padding(horizontal = Space.s, vertical = Space.xs),
                            color = colors.textSecondary, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                s.installmentInfo?.let { info ->
                    Text(
                        stringResource(R.string.installment_k_n, info.index, info.count) + " · " +
                            stringResource(R.string.installment_balance_total, AmountFormatter.format(info.balance, txCurrency), AmountFormatter.format(info.total, txCurrency)),
                        style = NexoType.Meta, color = colors.textSecondary, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs),
                    )
                }

                Spacer(Modifier.height(Space.l))
                // Field pills, in the configured input order, then when and how often.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    s.fields.filter { it != Field.AMOUNT && it != Field.FEE }.forEach { f ->
                        when (f) {
                            Field.ACCOUNT, Field.FROM, Field.TO -> {
                                val id = if (f == Field.TO) s.toAccountId else s.accountId
                                val label = stringResource(if (f == Field.ACCOUNT) R.string.account else if (f == Field.FROM) R.string.from else R.string.to)
                                val panel = EntryUi.panelFor(f)
                                FieldPill(
                                    text = id?.let { ref.accountById[it]?.name } ?: label,
                                    label = label,
                                    placeholder = id == null,
                                    active = s.panel == panel,
                                    accent = typeColor,
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    prefix = if (s.isTransfer && id != null) label else null,
                                ) { vm.openPanel(panel) }
                                if (s.isTransfer && f == s.fields.firstOrNull { it == Field.FROM || it == Field.TO }) {
                                    RoundIconButton(Icons.Rounded.SwapHoriz, stringResource(R.string.swap), { haptics.tick(); vm.swap() }, size = 40.dp, tint = colors.textSecondary)
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
                                    leading = visual?.let { v -> { CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark), size = 26.dp, over = colors.raised) } },
                                ) { vm.openPanel(Panel.CATEGORY) }
                            }
                            else -> Unit
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    FieldPill(
                        text = if (s.date.year == LocalDate.now().year) Formats.weekdayMonthDay(s.date) else Formats.mediumDate(s.date),
                        label = stringResource(R.string.date),
                        icon = Icons.Outlined.CalendarToday,
                        accent = typeColor,
                    ) { vm.enterEdit(); showDate = true }
                    if (ref.settings.timeDisplay.timeInput && s.showTime) {
                        FieldPill(
                            text = Formats.time(s.time, android.text.format.DateFormat.is24HourFormat(context)),
                            label = stringResource(R.string.date),
                            icon = Icons.Outlined.Schedule,
                            accent = typeColor,
                        ) { vm.enterEdit(); showTime = true }
                    }
                    if (s.showRepInst) {
                        Box {
                            val anySet = s.repeat != RepeatType.NONE || s.installments > 0
                            FieldPill(
                                text = when {
                                    s.repeat != RepeatType.NONE -> repeatLabel(s.repeat)
                                    s.installments > 0 -> pluralStringResource(R.plurals.n_months, s.installments, s.installments)
                                    else -> stringResource(R.string.rep_inst)
                                },
                                label = stringResource(R.string.rep_inst),
                                placeholder = !anySet,
                                icon = Icons.Rounded.Repeat,
                                accent = typeColor,
                                tint = if (anySet) typeColor else null,
                            ) {
                                when {
                                    s.repeatMode -> showRepeatList = true
                                    s.type == EntryType.EXPENSE -> showRepMenu = true
                                    !anySet -> showRepeatList = true
                                    else -> showRepMenu = true
                                }
                            }
                            NexoMenu(showRepMenu, onDismissRequest = { showRepMenu = false }) {
                                if (s.type == EntryType.EXPENSE && !s.isBalance) {
                                    NexoMenuItem(stringResource(R.string.installment), { showRepMenu = false; showInstallments = true }, Icons.Rounded.DateRange)
                                }
                                NexoMenuItem(stringResource(R.string.repeat), { showRepMenu = false; showRepeatList = true }, Icons.Rounded.Repeat)
                                if (s.repeat != RepeatType.NONE || s.installments > 0) {
                                    NexoMenuItem(stringResource(R.string.action_cancel), { showRepMenu = false; vm.clearRepInst() }, Icons.Rounded.Close, danger = true)
                                }
                            }
                        }
                    }
                    if (s.repeat != RepeatType.NONE) {
                        FieldPill(
                            text = s.repeatEnd?.let { stringResource(R.string.repeat_end) + " " + Formats.shortDate(it) } ?: stringResource(R.string.repeat_no_end),
                            label = stringResource(R.string.repeat_end),
                            accent = typeColor,
                            onClear = if (s.repeatEnd != null) ({ vm.setRepeatEnd(null) }) else null,
                        ) { showRepeatEnd = true }
                    }
                    // Transfer fee, or a bank fee on an SMS expense.
                    if (s.feeAllowed && s.feeExpanded) {
                        val feeCur = ref.currency(s.feeCurrencyId)
                        val text = if (s.panel == Panel.FEE && s.pad.buffer.isNotEmpty()) bufferParts(s.pad.buffer, feeCur).text
                        else s.feeAmount?.let { AmountFormatter.format(it, feeCur) } ?: ""
                        FieldPill(
                            text = stringResource(R.string.fees) + (if (text.isNotEmpty()) " · $text" else ""),
                            label = stringResource(R.string.fees),
                            active = s.panel == Panel.FEE,
                            accent = typeColor,
                            onClear = { vm.setFeeExpanded(false) },
                        ) { vm.openPanel(Panel.FEE) }
                    } else if (s.feeAllowed && s.repeat == RepeatType.NONE) {
                        FieldPill(text = stringResource(R.string.fees), label = stringResource(R.string.fees), placeholder = true, icon = Icons.Rounded.Add, accent = typeColor) { vm.setFeeExpanded(true) }
                    }
                }

                // Details: tags and Scan receipt as pills, then the description and any photos already attached.
                // Same gap and side padding as the rows above, so all the pill rows read as one evenly spaced block.
                Spacer(Modifier.height(Space.s))
                val photosAllowed = !s.repeatMode && s.repeat == RepeatType.NONE
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Space.l),
                    horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    if (s.repeat == RepeatType.NONE) {
                        s.tagIds.mapNotNull { id -> ref.tagById[id]?.takeIf { !it.isDeleted } }.forEach { t ->
                            FieldPill(text = "#" + t.name, label = stringResource(R.string.tags), accent = typeColor, onClear = { vm.removeTag(t.id) }) { vm.enterEdit(); showTags = true }
                        }
                        FieldPill(text = stringResource(R.string.tags), label = stringResource(R.string.tags), placeholder = true, icon = Icons.Rounded.Add, accent = typeColor) { vm.enterEdit(); showTags = true }
                    }
                    if (photosAllowed && s.scanAllowed) {
                        Box {
                            FieldPill(text = stringResource(R.string.receipt_scan), label = stringResource(R.string.receipt_scan), placeholder = true, icon = Icons.Outlined.DocumentScanner, accent = typeColor) {
                                vm.enterEdit()
                                if (device?.photoNoticeShown == false) showPhotoNotice = true else launchScanner()
                            }
                            NexoMenu(showScanMenu, onDismissRequest = { showScanMenu = false }) {
                                if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                                    NexoMenuItem(stringResource(R.string.camera), { showScanMenu = false; launchCamera() }, Icons.Rounded.PhotoCamera)
                                }
                                NexoMenuItem(stringResource(R.string.gallery), {
                                    showScanMenu = false
                                    context.expectReturn()
                                    scanGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }, Icons.Rounded.PhotoLibrary)
                            }
                        }
                    }
                }
                if (photosAllowed && s.photos.isNotEmpty()) {
                    Box(Modifier.padding(horizontal = Space.gutter, vertical = Space.m)) {
                        PhotoThumbs(s.photos, vm.photoStore, removable = !s.viewing, onOpen = { viewerIndex = it }, onRemove = vm::removePhoto, onLongPress = vm::enterEdit)
                    }
                }
                TextField(
                    value = s.description,
                    onValueChange = vm::setDescription,
                    placeholder = { Text(stringResource(R.string.description), color = colors.textTertiary) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null, tint = colors.textTertiary, modifier = Modifier.size(20.dp)) },
                    minLines = 1,
                    colors = quietFieldColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.s, vertical = Space.xs).onFocusChanged { if (it.isFocused) { vm.closePanel(); vm.enterEdit() } },
                )
                s.fxNote?.let { note ->
                    Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                        Text(note, style = NexoType.Meta, color = colors.textSecondary)
                        if (s.fxFromApi) {
                            Text(
                                stringResource(R.string.fx_attribution),
                                Modifier.clickable { com.nexo.app.ui.sms.openRatesAttribution(context) }.padding(top = Space.xs),
                                style = NexoType.Meta, color = colors.textTertiary,
                            )
                        }
                    }
                }
                s.sourceText?.let { src ->
                    Column(Modifier.padding(horizontal = Space.gutter, vertical = Space.s)) {
                        Text(stringResource(R.string.original_text).uppercase(Locale.getDefault()), style = NexoType.Caps, color = colors.textTertiary)
                        Spacer(Modifier.height(Space.xs))
                        Text(src, Modifier.fillMaxWidth().clip(NexoShapes.control).background(colors.raised).padding(Space.m), style = NexoType.Meta, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(Space.xl))
            }

            // Bottom: keypad, pickers or actions, swapped with a short rise.
            AnimatedContent(
                targetState = when (s.panel) {
                    Panel.AMOUNT, Panel.FEE -> BottomKind.PAD
                    Panel.ACCOUNT, Panel.TO_ACCOUNT -> BottomKind.ACCOUNT
                    Panel.CATEGORY -> BottomKind.CATEGORY
                    Panel.NONE -> BottomKind.ACTIONS
                },
                transitionSpec = {
                    // The old content is gone before the new one shows, so the two never overlap.
                    (slideInVertically(NexoMotion.gentle()) { it / 5 } + fadeIn(tween(180, delayMillis = 90))) togetherWith
                        (slideOutVertically(tween(140)) { it / 6 } + fadeOut(tween(90))) using
                        SizeTransform(clip = true) { _, _ -> spring(dampingRatio = 1f, stiffness = 420f) }
                },
                label = "entryBottom",
            ) { kind ->
                when (kind) {
                    BottomKind.ACCOUNT -> AccountPanel(
                        title = stringResource(if (!s.isTransfer) R.string.account else if (s.panel == Panel.TO_ACCOUNT) R.string.to else R.string.from),
                        ref = ref,
                        selectedId = if (s.panel == Panel.TO_ACCOUNT) s.toAccountId else s.accountId,
                        grouped = device?.groupedAccountPicker == true,
                        accent = typeColor,
                        onToggleGrouped = { vm.setGroupedPicker(device?.groupedAccountPicker != true) },
                        onSelect = vm::selectAccount,
                        onEdit = { nav.navigate(AccountListKey()) },
                        onAdd = { nav.navigate(AccountFormKey()) },
                        onClose = vm::closePanel,
                    )
                    BottomKind.CATEGORY -> CategoryPanel(
                        kind = if (s.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                        ref = ref,
                        selectedId = s.categoryId,
                        accent = typeColor,
                        onSelect = vm::selectCategory,
                        onEdit = { nav.navigate(CategoryListKey(if (s.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE)) },
                        onClose = vm::closePanel,
                    )
                    BottomKind.PAD -> {
                        val fee = s.panel == Panel.FEE
                        val cur = ref.currency(if (fee) s.feeCurrencyId else s.currencyId)
                        NumberPad(
                            title = stringResource(if (fee) R.string.fees else R.string.amount),
                            onKey = ::pressKey,
                            onDone = vm::padDone,
                            onCalculator = { showCalc = true },
                            descending = device?.numberPadDescending == true,
                            decimals = cur.decimals,
                            signKey = !fee,
                            currencies = ref.activeCurrencies,
                            selectedCurrencyId = cur.id,
                            onCurrency = { vm.setPadCurrency(it.id) },
                            onGlobe = { nav.navigate(SubCurrenciesKey) },
                            accent = typeColor,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                    BottomKind.ACTIONS -> BottomButtons(
                        s = s,
                        onSave = { haptics.confirm(); vm.save(false) },
                        onContinue = { haptics.confirm(); vm.save(true) },
                        onDelete = { vm.setAskDelete(true) },
                        onCopy = { showCopy = true },
                        onBookmark = { haptics.confirm(); vm.bookmark() },
                        onSkip = vm::skip,
                        review = key.reviewUnassigned,
                        onDeny = vm::denySms,
                    )
                }
            }
        }

        // Scan receipt's confirm sheet. The last reading stays drawn while the sheet slides away.
        val shownScan = remember { arrayOfNulls<ScanUi>(1) }
        if (s.scan != null) shownScan[0] = s.scan
        InlineBottomSheet(visible = s.scan != null, onDismiss = vm::dismissScan, heightFraction = 0.62f) {
            shownScan[0]?.let { scan ->
                ReceiptSheetContent(scan, txCurrency, noteEmpty = s.note.isBlank(), onUse = vm::applyReceipt, onCancel = vm::dismissScan)
            }
        }
    }

    // Dialogs
    if (showDate) NexoDatePickerDialog(s.date, onPick = vm::setDate, onDismiss = { showDate = false })
    if (showTime) NexoTimePickerDialog(s.time, onPick = vm::setTime, onDismiss = { showTime = false })
    if (showRepeatEnd) NexoDatePickerDialog(s.repeatEnd ?: s.date.plusYears(1), onPick = { vm.setRepeatEnd(if (it.isBefore(s.date)) s.date else it) }, onDismiss = { showRepeatEnd = false })
    if (showCalc) CalculatorDialog(if (s.panel == Panel.FEE) s.feeAmount else s.amount, onResult = vm::calculatorResult, onDismiss = { showCalc = false })
    if (showRepeatList) {
        com.nexo.app.ui.designsystem.component.ChoiceDialog(
            title = stringResource(R.string.repeat),
            options = RepeatType.pickerOrder,
            selected = s.repeat,
            label = { repeatLabel(it) },
            onSelect = vm::setRepeat,
            onDismiss = { showRepeatList = false },
        )
    }
    if (showInstallments) {
        AmountSheet(
            title = stringResource(R.string.monthly_installment_plan),
            initial = s.installments.takeIf { it > 0 }?.toDouble(),
            decimals = 0,
            allowSign = false,
            calculator = false,
            maxLength = 3,
            validate = { (it ?: 0.0) <= 600 },
            format = { pluralStringResourceText(resources, it.toInt()) },
            onConfirm = { vm.setInstallments((it ?: 0.0).toInt()) },
            onDismiss = { showInstallments = false },
        )
    }
    if (rateEdit != 0) {
        val amount = s.amount ?: 0.0
        val rate = s.rate ?: Conversion.effectiveRate(txCurrency)
        if (rateEdit == 1) {
            AmountSheet(
                title = stringResource(R.string.main_currency_amount),
                initial = Money.round(amount * rate, ref.mainCurrency.decimals),
                decimals = ref.mainCurrency.decimals,
                allowSign = false,
                onConfirm = { it?.let(vm::setMainAmount) },
                onDismiss = { rateEdit = 0 },
            )
        } else {
            AmountSheet(
                title = stringResource(R.string.exchange_rate),
                initial = rate,
                decimals = 10,
                allowSign = false,
                format = { AmountFormatter.rate(it) },
                onConfirm = { it?.takeIf { v -> v > 0 }?.let(vm::setRate) },
                onDismiss = { rateEdit = 0 },
            )
        }
    }
    if (showTags) TagSelectSheet(tags, s.tagIds, vm.tagRepo, onDone = vm::setTags, onDismiss = { showTags = false })
    if (viewerIndex >= 0) PhotoViewer(s.photos, viewerIndex, vm.photoStore, onDismiss = { viewerIndex = -1 })
    if (showPhotoNotice) {
        AlertDialog(
            onDismissRequest = { showPhotoNotice = false },
            text = { Text(stringResource(R.string.photo_notice)) },
            confirmButton = { TextButton(onClick = { showPhotoNotice = false; vm.markPhotoNoticeShown(); launchScanner() }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (showTransferGuide) {
        AlertDialog(
            onDismissRequest = { showTransferGuide = false; vm.markTransferGuideShown() },
            title = { Text(stringResource(R.string.transfer)) },
            text = { Text(stringResource(R.string.transfer_guide)) },
            confirmButton = { TextButton(onClick = { showTransferGuide = false; vm.markTransferGuideShown() }) { Text(stringResource(R.string.action_ok)) } },
        )
    }
    if (s.confirmRepeat) {
        ConfirmDialog(
            message = stringResource(if (ref.settings.repeatReflect == com.nexo.app.core.model.RepeatReflect.ON_DATE) R.string.repeat_reflect_on_date else R.string.repeat_reflect_first_day) +
                "\n\n" + stringResource(R.string.repeat_save_question),
            onConfirm = vm::confirmRepeatSave,
            onDismiss = vm::dismissRepeatConfirm,
        )
    }
    if (s.askDelete) {
        if (s.installmentInfo != null) {
            AlertDialog(
                onDismissRequest = { vm.setAskDelete(false) },
                text = { Text(stringResource(R.string.confirm_delete)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = { vm.setAskDelete(false); haptics.confirm(); vm.delete(true) }) { Text(stringResource(R.string.delete_all)) }
                        TextButton(onClick = { vm.setAskDelete(false); haptics.confirm(); vm.delete(false) }) { Text(stringResource(R.string.action_delete)) }
                    }
                },
                dismissButton = { TextButton(onClick = { vm.setAskDelete(false) }) { Text(stringResource(R.string.action_cancel)) } },
            )
        } else {
            ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { haptics.confirm(); vm.delete(false) }, onDismiss = { vm.setAskDelete(false) })
        }
    }
    if (showCopy) {
        var today by remember { mutableStateOf(device?.copyWithToday ?: true) }
        AlertDialog(
            onDismissRequest = { showCopy = false },
            title = { Text(stringResource(R.string.copy)) },
            text = {
                Column {
                    listOf(true to R.string.copy_today, false to R.string.copy_original).forEach { (v, label) ->
                        Row(Modifier.fillMaxWidth().clickable { today = v }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = today == v, onClick = null)
                            Text(stringResource(label), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCopy = false; vm.copy(today) }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = { TextButton(onClick = { showCopy = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private enum class BottomKind { PAD, ACCOUNT, CATEGORY, ACTIONS }

private fun pluralStringResourceText(resources: android.content.res.Resources, n: Int): String =
    resources.getQuantityString(R.plurals.n_months, n, n)

private fun categoryText(ref: RefData, s: EntryUi): String {
    if (s.isBalance || (s.isBalanceAdjustment && s.categoryId == null)) return "—"
    val id = s.categoryId ?: return ""
    val main = ref.mainCategoryId(id)?.let { ref.categoryName(it) } ?: return ""
    val sub = ref.subCategoryId(id)?.let { ref.categoryName(it) }
    return if (sub != null) "$main › $sub" else main
}

/** The keypad buffer as display parts: locale grouping, the typed decimals, the currency symbol. */
private fun bufferParts(buffer: String, currency: CurrencyEntity): AmountParts {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val negative = buffer.startsWith("-")
    val body = buffer.removePrefix("-")
    val intPart = body.substringBefore('.')
    val grouped = intPart.toBigIntegerOrNull()?.let { NumberFormat.getIntegerInstance().format(it) } ?: intPart.ifEmpty { "0" }
    val fraction = if (body.contains('.')) separator + body.substringAfter('.') else ""
    val prefix = currency.symbolPosition == SymbolPosition.PREFIX
    return AmountParts(if (negative) AmountParts.MINUS else "", if (prefix) currency.symbol else "", grouped, fraction, if (prefix) "" else currency.symbol)
}

/** "0.00" in the currency's shape, shown dimmed before anything is typed. */
private fun placeholderParts(currency: CurrencyEntity): AmountParts {
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator
    val prefix = currency.symbolPosition == SymbolPosition.PREFIX
    val fraction = if (currency.decimals > 0) separator + "0".repeat(currency.decimals) else ""
    return AmountParts("", if (prefix) currency.symbol else "", "0", fraction, if (prefix) "" else currency.symbol)
}

/**
 * The amount as the screen's centrepiece. Each keystroke gives it a small punch; a rejected key
 * shakes it. While the keypad is open a short bar under it breathes in the entry's colour.
 */
@Composable
private fun AmountDisplay(s: EntryUi, currency: CurrencyEntity, color: Color, shake: Int, onClick: () -> Unit) {
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
    val tint by animateColorAsState(if (parts == null) colors.textTertiary else color, NexoMotion.standard(), label = "amountTint")
    Column(
        Modifier.fillMaxWidth().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.shakeOn(shake).graphicsLayer { scaleX = punch.value; scaleY = punch.value }, contentAlignment = Alignment.Center) {
            AmountText(parts ?: placeholderParts(currency), Modifier.shrinkToFit(), style = NexoType.Hero, color = tint, minorScale = 0.5f)
        }
        AnimatedVisibility(active, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            val pulse = rememberInfiniteTransition(label = "caret")
            val alpha by pulse.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "caretAlpha")
            Box(Modifier.padding(top = Space.s).size(width = 28.dp, height = 3.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        }
    }
}

/** The note, set as a centred title above the amount. */
@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit, focused: Boolean, modifier: Modifier, onDone: () -> Unit, onClear: () -> Unit) {
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
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = Space.s),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text(stringResource(R.string.note), style = style.copy(color = colors.textTertiary), modifier = Modifier.fillMaxWidth())
                    inner()
                }
            },
        )
        if (focused && value.isNotEmpty()) {
            RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_clear), onClear, modifier = Modifier.align(Alignment.CenterEnd), size = 28.dp, tint = colors.textSecondary)
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
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val bg by animateColorAsState(if (active) colors.tint(accent, colors.raised) else colors.raised, NexoMotion.standard(), label = "pillBg")
    val ring by animateColorAsState(if (active) accent else Color.Transparent, NexoMotion.standard(), label = "pillRing")
    Row(
        modifier
            .pressScale(interactions, 0.96f)
            .height(40.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, ring, CircleShape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (placeholder) label else "$label: $text" }
            .padding(start = if (leading != null || icon != null) Space.s else Space.m + Space.xxs, end = if (onClear != null) Space.xs else Space.m + Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> { leading(); Spacer(Modifier.width(Space.s)) }
            icon != null -> { Icon(icon, null, tint = tint ?: colors.textSecondary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.s - Space.xxs)) }
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
            RoundIconButton(Icons.Rounded.Close, stringResource(R.string.action_clear), onClear, size = 28.dp, tint = colors.textTertiary, container = Color.Transparent)
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

@Composable
private fun BottomButtons(
    s: EntryUi,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onSkip: () -> Unit,
    review: Boolean,
    onDeny: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val sms = s.mode == EntryMode.SMS
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.gutter, vertical = Space.m),
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        if (s.isEdit && s.viewing && !review) {
            SoftButton(stringResource(R.string.action_delete), onDelete, Modifier.weight(1f), content = MaterialTheme.colorScheme.error)
            SoftButton(stringResource(R.string.copy), onCopy, Modifier.weight(1f))
            SoftButton(stringResource(R.string.bookmark), onBookmark, Modifier.weight(1f))
        } else {
            if (review) {
                SoftButton(stringResource(R.string.skip), onSkip, Modifier.weight(1f))
                SoftButton(stringResource(R.string.action_delete), onDelete, Modifier.weight(1f), content = MaterialTheme.colorScheme.error)
            }
            if (sms) SoftButton(stringResource(R.string.sms_deny), onDeny, Modifier.weight(1f), enabled = !s.saving)
            val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            Button(
                onClick = onSave,
                enabled = !s.saving,
                interactionSource = interactions,
                modifier = Modifier.weight(if (review) 1.4f else 2f).height(52.dp).pressScale(interactions, 0.97f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) {
                Text(
                    when {
                        review -> stringResource(R.string.save_n, s.unassignedCount)
                        sms -> stringResource(R.string.sms_approve)
                        else -> stringResource(R.string.action_save)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (s.showContinue && !review) {
                SoftButton(stringResource(R.string.action_continue), onContinue, Modifier.weight(1f), enabled = !s.saving)
            }
        }
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier, content: Color = LocalNexoColors.current.text, enabled: Boolean = true) {
    val colors = LocalNexoColors.current
    val interactions = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactions,
        modifier = modifier.height(52.dp).pressScale(interactions, 0.97f),
        colors = ButtonDefaults.buttonColors(containerColor = colors.raised, contentColor = content),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Takes a photo with the phone's camera app, asking it to open on the rear lens. The system has no
 * official way to choose the lens for another app, so these extras are hints: many camera apps
 * follow them, and one that does not simply opens on whichever lens it used last.
 */
private class RearCameraPicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: android.content.Context, input: Uri): android.content.Intent =
        super.createIntent(context, input).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
            putExtra("android.intent.extras.LENS_FACING_BACK", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
            putExtra("camerafacing", "rear")
            putExtra("default_camera", "0")
        }
}

/** One page, straightened and cropped, from the rear camera or the gallery. */
private fun receiptScanner() = GmsDocumentScanning.getClient(
    GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build(),
)

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
