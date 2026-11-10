package com.nexo.app.ui.entry

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.RepeatType
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.core.money.Money
import com.nexo.app.domain.Conversion
import com.nexo.app.domain.PadKey
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.AmountSheet
import com.nexo.app.ui.components.CalculatorDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.Formats
import com.nexo.app.ui.components.NexoDatePickerDialog
import com.nexo.app.ui.components.NexoTimePickerDialog
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.NumberPad
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SubCurrenciesKey
import com.nexo.app.ui.theme.LocalMoneyColors
import java.io.File
import java.text.DecimalFormatSymbols

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(key: EntryKey) {
    val vm = hiltViewModel<EntryViewModel, EntryViewModel.Factory>(creationCallback = { it.create(key) })
    val s by vm.ui.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val tags by vm.tagRepo.tags.collectAsStateWithLifecycle(emptyList())
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val money = LocalMoneyColors.current
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
    var showPhotoMenu by rememberSaveable { mutableStateOf(false) }
    var showPhotoNotice by rememberSaveable { mutableStateOf(false) }
    var viewerIndex by rememberSaveable { mutableIntStateOf(-1) }
    var rateEdit by rememberSaveable { mutableIntStateOf(0) }
    var showTransferGuide by rememberSaveable { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is EntryEvent.Message -> Toast.makeText(
                    context,
                    if (e.arg != null) resources.getString(e.text, e.arg) else resources.getString(e.text),
                    Toast.LENGTH_SHORT,
                ).show()
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
        else -> MaterialTheme.colorScheme.onSurface
    }
    val txCurrency = ref.currency(s.currencyId)
    val title = when {
        s.balanceKind == TxKind.BALANCE_INCREASE -> stringResource(R.string.income_balance)
        s.balanceKind == TxKind.BALANCE_DECREASE -> stringResource(R.string.expense_balance)
        s.mode == EntryMode.PAY_CARD -> stringResource(R.string.card_payment)
        s.repeatMode -> stringResource(R.string.repeat)
        s.type == EntryType.INCOME -> stringResource(R.string.income)
        s.type == EntryType.EXPENSE -> stringResource(R.string.expense)
        else -> stringResource(R.string.transfer)
    }

    // Photos
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { vm.addPhotos(listOf(Uri.parse(it))) }
    }
    val remaining = (3 - s.photos.size).coerceAtLeast(1)
    val galleryMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, remaining))) { uris ->
        val ok = uris.filter { vm.photoStore.isSupported(it) }
        if (ok.size < uris.size) Toast.makeText(context, R.string.unsupported_file_format, Toast.LENGTH_SHORT).show()
        vm.addPhotos(ok.take(remaining))
    }
    val galleryOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            if (vm.photoStore.isSupported(uri)) vm.addPhotos(listOf(uri)) else Toast.makeText(context, R.string.unsupported_file_format, Toast.LENGTH_SHORT).show()
        }
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "temp").apply { mkdirs() }
        val file = File(dir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri = uri.toString()
        camera.launch(uri)
    }

    if (!s.loaded) return

    Scaffold(
        topBar = {
            NexoTopBar(title = title, onBack = { nav.back() }) {
                if ((s.mode == EntryMode.NEW || s.mode == EntryMode.PAY_CARD || s.mode == EntryMode.FAVORITE) && s.originalId == null) {
                    IconButton(onClick = { nav.navigate(BookmarksKey(pickForForm = true)) }) { Icon(Icons.Outlined.StarOutline, stringResource(R.string.bookmarks)) }
                }
                if (s.isEdit && !s.viewing) {
                    IconButton(onClick = vm::revert) { Icon(Icons.Filled.Close, stringResource(R.string.revert)) }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()
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
                    vm.padKey(k)
                    true
                },
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (!s.isBalance) {
                    TypeToggles(s.type, money.income, money.expense, enabled = s.mode != EntryMode.PAY_CARD) { t ->
                        if (t == EntryType.TRANSFER && device?.transferGuideShown == false) showTransferGuide = true
                        vm.setType(t)
                    }
                } else {
                    TypeToggles(s.type, money.income, money.expense, enabled = true, highlight = false) { vm.setType(it) }
                }
                // Date row
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.date), Modifier.width(88.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Formats.dateWithWeekday(s.date),
                        Modifier.clickable { vm.enterEdit(); showDate = true }.padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (ref.settings.timeDisplay.timeInput && s.showTime) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            Formats.time(s.time, android.text.format.DateFormat.is24HourFormat(context)),
                            Modifier.clickable { vm.enterEdit(); showTime = true }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    s.installmentInfo?.let {
                        Text(stringResource(R.string.installment_k_n, it.index, it.count), color = typeColor, style = MaterialTheme.typography.labelLarge)
                    }
                    if (s.showRepInst) {
                        Box {
                            val label = when {
                                s.repeat != RepeatType.NONE -> repeatLabel(s.repeat)
                                s.installments > 0 -> pluralStringResource(R.plurals.n_months, s.installments, s.installments)
                                else -> stringResource(R.string.rep_inst)
                            }
                            TextButton(onClick = {
                                val anySet = s.repeat != RepeatType.NONE || s.installments > 0
                                when {
                                    s.repeatMode -> showRepeatList = true
                                    s.type == EntryType.EXPENSE -> showRepMenu = true
                                    !anySet -> showRepeatList = true
                                    else -> showRepMenu = true
                                }
                            }) {
                                Text(label, color = if (s.repeat != RepeatType.NONE || s.installments > 0) typeColor else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(showRepMenu, onDismissRequest = { showRepMenu = false }) {
                                if (s.type == EntryType.EXPENSE && !s.isBalance) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.installment)) }, onClick = { showRepMenu = false; showInstallments = true })
                                }
                                DropdownMenuItem(text = { Text(stringResource(R.string.repeat)) }, onClick = { showRepMenu = false; showRepeatList = true })
                                if (s.repeat != RepeatType.NONE || s.installments > 0) {
                                    DropdownMenuItem(text = { Text(stringResource(R.string.action_cancel)) }, onClick = { showRepMenu = false; vm.clearRepInst() })
                                }
                            }
                        }
                    }
                }
                if (s.repeat != RepeatType.NONE) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.repeat_end), Modifier.width(88.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            s.repeatEnd?.let { Formats.dateWithWeekday(it) } ?: stringResource(R.string.repeat_no_end),
                            Modifier.clickable { showRepeatEnd = true }.padding(vertical = 10.dp),
                        )
                        if (s.repeatEnd != null) IconButton(onClick = { vm.setRepeatEnd(null) }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Account / category / amount block
                s.fields.forEach { f ->
                    when (f) {
                        Field.ACCOUNT, Field.FROM, Field.TO, Field.CATEGORY -> {
                            val label = stringResource(
                                when (f) {
                                    Field.ACCOUNT -> R.string.account
                                    Field.FROM -> R.string.from
                                    Field.TO -> R.string.to
                                    else -> R.string.category
                                },
                            )
                            val value = when (f) {
                                Field.ACCOUNT, Field.FROM -> s.accountId?.let { ref.accountById[it]?.name } ?: ""
                                Field.TO -> s.toAccountId?.let { ref.accountById[it]?.name } ?: ""
                                else -> categoryText(ref, s)
                            }
                            val panel = EntryUi.panelFor(f)
                            FieldRow(label, value, active = s.panel == panel, accent = typeColor, onClick = { vm.openPanel(panel) }) {
                                if (s.isTransfer && f == s.fields.firstOrNull { it == Field.FROM || it == Field.TO }) {
                                    IconButton(onClick = vm::swap) { Icon(Icons.Filled.SwapVert, stringResource(R.string.swap)) }
                                }
                            }
                        }
                        Field.AMOUNT -> {
                            val text = if (s.panel == Panel.AMOUNT && s.pad.buffer.isNotEmpty()) bufferText(s.pad.buffer, txCurrency.symbol, txCurrency.symbolPosition)
                            else s.amount?.let { AmountFormatter.format(it, txCurrency) } ?: ""
                            FieldRow(stringResource(R.string.amount), text, active = s.panel == Panel.AMOUNT, accent = typeColor, onClick = { vm.openPanel(Panel.AMOUNT) }, bold = true) {
                                if (s.isTransfer && !s.feeExpanded && s.repeat == RepeatType.NONE) {
                                    TextButton(onClick = { vm.setFeeExpanded(true) }) { Text(stringResource(R.string.fees)) }
                                }
                            }
                            // Exchange lines (TX-010)
                            val amount = s.amount
                            if (txCurrency.id != ref.mainCurrency.id && amount != null && amount != 0.0 && s.repeat == RepeatType.NONE) {
                                val rate = s.rate ?: Conversion.effectiveRate(txCurrency)
                                val main = Money.round(Money.multiply(amount, rate), ref.mainCurrency.decimals)
                                Column(Modifier.padding(start = 88.dp, bottom = 6.dp)) {
                                    Text(AmountFormatter.format(main, ref.mainCurrency), Modifier.clickable { rateEdit = 1 }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.exchange_rate_value, AmountFormatter.rate(rate)), Modifier.clickable { rateEdit = 2 }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Field.FEE -> {
                            val feeCur = ref.currency(s.feeCurrencyId)
                            val text = if (s.panel == Panel.FEE && s.pad.buffer.isNotEmpty()) bufferText(s.pad.buffer, feeCur.symbol, feeCur.symbolPosition)
                            else s.feeAmount?.let { AmountFormatter.format(it, feeCur) } ?: ""
                            FieldRow(stringResource(R.string.fees), text, active = s.panel == Panel.FEE, accent = typeColor, onClick = { vm.openPanel(Panel.FEE) }) {
                                IconButton(onClick = { vm.setFeeExpanded(false) }) { Icon(Icons.Filled.Close, stringResource(R.string.remove_fee)) }
                            }
                        }
                    }
                }
                s.installmentInfo?.let { info ->
                    Text(
                        stringResource(R.string.installment_balance_total, AmountFormatter.format(info.balance, txCurrency), AmountFormatter.format(info.total, txCurrency)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                // Note with autocomplete (TX-012)
                TextField(
                    value = s.note,
                    onValueChange = vm::setNote,
                    label = { Text(stringResource(R.string.note)) },
                    singleLine = true,
                    trailingIcon = if (s.note.isNotEmpty() && noteFocused) ({ IconButton(onClick = { vm.setNote("") }) { Icon(Icons.Filled.Close, stringResource(R.string.action_clear)) } }) else null,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
                    colors = flatFieldColors(),
                    modifier = Modifier.fillMaxWidth().focusRequester(noteFocus).onFocusChanged {
                        noteFocused = it.isFocused
                        if (it.isFocused) { vm.closePanel(); vm.enterEdit() }
                    },
                )
                if (noteFocused && s.note.isNotEmpty()) {
                    val q = s.note
                    val matches = remember(q, suggestions) {
                        suggestions.mapNotNull { sug -> sug.note.indexOf(q, ignoreCase = true).takeIf { it >= 0 && sug.note != q }?.let { sug to it } }
                            .sortedWith(compareByDescending<Pair<NoteSuggestion, Int>> { it.first.count }.thenBy { it.second }.thenByDescending { it.first.lastDate })
                            .take(6)
                    }
                    matches.forEach { (sug, idx) ->
                        Text(
                            buildAnnotatedString {
                                append(sug.note.substring(0, idx))
                                withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)) { append(sug.note.substring(idx, idx + q.length)) }
                                append(sug.note.substring(idx + q.length))
                            },
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).clickable { vm.setNote(sug.note); focus.clearFocus() }.padding(12.dp),
                        )
                    }
                }

                // Tags (TX-014)
                if (s.repeat == RepeatType.NONE) {
                    FlowRow(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.tagIds.mapNotNull { id -> ref.tagById[id]?.takeIf { !it.isDeleted } }.forEach { t ->
                            InputChip(selected = false, onClick = { vm.removeTag(t.id) }, label = { Text("#" + t.name) }, trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) })
                        }
                        InputChip(selected = false, onClick = { vm.enterEdit(); showTags = true }, label = { Text("+ " + stringResource(R.string.tags)) })
                    }
                }
                TextField(
                    value = s.description,
                    onValueChange = vm::setDescription,
                    label = { Text(stringResource(R.string.description)) },
                    minLines = 2,
                    colors = flatFieldColors(),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) { vm.closePanel(); vm.enterEdit() } },
                )

                // Photos (TX-015)
                if (!s.repeatMode && s.repeat == RepeatType.NONE) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (s.photos.size < 3) {
                            Box {
                                OutlinedButton(onClick = {
                                    vm.enterEdit()
                                    if (device?.photoNoticeShown == false) showPhotoNotice = true else showPhotoMenu = true
                                }, modifier = Modifier.size(72.dp), shape = RoundedCornerShape(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                    Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.add_photo))
                                }
                                DropdownMenu(showPhotoMenu, onDismissRequest = { showPhotoMenu = false }) {
                                    if (context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)) {
                                        DropdownMenuItem(text = { Text(stringResource(R.string.camera)) }, onClick = { showPhotoMenu = false; launchCamera() })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.gallery)) }, onClick = {
                                        showPhotoMenu = false
                                        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        if (3 - s.photos.size > 1) galleryMany.launch(req) else galleryOne.launch(req)
                                    })
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        PhotoThumbs(s.photos, vm.photoStore, removable = !s.viewing, onOpen = { viewerIndex = it }, onRemove = vm::removePhoto, onLongPress = vm::enterEdit)
                    }
                }
                s.sourceText?.let { src ->
                    Text(stringResource(R.string.original_text), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(src, Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)).padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
            }

            // Bottom: panel or buttons
            when (s.panel) {
                Panel.ACCOUNT, Panel.TO_ACCOUNT -> AccountPanel(
                    title = stringResource(if (!s.isTransfer) R.string.account else if (s.panel == Panel.ACCOUNT) R.string.from else R.string.to),
                    ref = ref,
                    selectedId = if (s.panel == Panel.ACCOUNT) s.accountId else s.toAccountId,
                    grouped = device?.groupedAccountPicker == true,
                    accent = typeColor,
                    onToggleGrouped = { vm.setGroupedPicker(device?.groupedAccountPicker != true) },
                    onSelect = vm::selectAccount,
                    onEdit = { nav.navigate(AccountListKey()) },
                    onAdd = { nav.navigate(AccountFormKey()) },
                    onClose = vm::closePanel,
                )
                Panel.CATEGORY -> CategoryPanel(
                    kind = if (s.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE,
                    ref = ref,
                    selectedId = s.categoryId,
                    accent = typeColor,
                    onSelect = vm::selectCategory,
                    onEdit = { nav.navigate(CategoryListKey(if (s.type == EntryType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE)) },
                    onClose = vm::closePanel,
                )
                Panel.AMOUNT, Panel.FEE -> {
                    val fee = s.panel == Panel.FEE
                    val cur = ref.currency(if (fee) s.feeCurrencyId else s.currencyId)
                    NumberPad(
                        title = stringResource(if (fee) R.string.fees else R.string.amount),
                        onKey = vm::padKey,
                        onDone = vm::padDone,
                        onCalculator = { showCalc = true },
                        descending = device?.numberPadDescending == true,
                        decimals = cur.decimals,
                        signKey = !fee,
                        currencies = ref.activeCurrencies,
                        selectedCurrencyId = cur.id,
                        onCurrency = { vm.setPadCurrency(it.id) },
                        onGlobe = { nav.navigate(SubCurrenciesKey) },
                        accent = if (s.type == EntryType.TRANSFER) MaterialTheme.colorScheme.primary else typeColor,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
                Panel.NONE -> BottomButtons(
                    s = s,
                    color = if (s.type == EntryType.TRANSFER) MaterialTheme.colorScheme.primary else typeColor,
                    onSave = { vm.save(false) },
                    onContinue = { vm.save(true) },
                    onDelete = { vm.setAskDelete(true) },
                    onCopy = { showCopy = true },
                    onBookmark = vm::bookmark,
                    onSkip = vm::skip,
                    review = key.reviewUnassigned,
                )
            }
        }
    }

    // Dialogs
    if (showDate) NexoDatePickerDialog(s.date, onPick = vm::setDate, onDismiss = { showDate = false })
    if (showTime) NexoTimePickerDialog(s.time, onPick = vm::setTime, onDismiss = { showTime = false })
    if (showRepeatEnd) NexoDatePickerDialog(s.repeatEnd ?: s.date.plusYears(1), onPick = { vm.setRepeatEnd(if (it.isBefore(s.date)) s.date else it) }, onDismiss = { showRepeatEnd = false })
    if (showCalc) CalculatorDialog(if (s.panel == Panel.FEE) s.feeAmount else s.amount, onResult = vm::calculatorResult, onDismiss = { showCalc = false })
    if (showRepeatList) {
        com.nexo.app.ui.components.ChoiceDialog(
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
            confirmButton = { TextButton(onClick = { showPhotoNotice = false; vm.markPhotoNoticeShown(); showPhotoMenu = true }) { Text(stringResource(R.string.action_ok)) } },
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
                        TextButton(onClick = { vm.setAskDelete(false); vm.delete(true) }) { Text(stringResource(R.string.delete_all)) }
                        TextButton(onClick = { vm.setAskDelete(false); vm.delete(false) }) { Text(stringResource(R.string.action_delete)) }
                    }
                },
                dismissButton = { TextButton(onClick = { vm.setAskDelete(false) }) { Text(stringResource(R.string.action_cancel)) } },
            )
        } else {
            ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.delete(false) }, onDismiss = { vm.setAskDelete(false) })
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

private fun pluralStringResourceText(resources: android.content.res.Resources, n: Int): String =
    resources.getQuantityString(R.plurals.n_months, n, n)

private fun categoryText(ref: RefData, s: EntryUi): String {
    if (s.isBalance || (s.isBalanceAdjustment && s.categoryId == null)) return "-"
    val id = s.categoryId ?: return ""
    val main = ref.mainCategoryId(id)?.let { ref.categoryName(it) } ?: return ""
    val sub = ref.subCategoryId(id)?.let { ref.categoryName(it) }
    return if (sub != null) "$main/$sub" else main
}

/** Buffer shown while typing: locale grouping and the currency symbol. */
private fun bufferText(buffer: String, symbol: String, position: com.nexo.app.core.model.SymbolPosition): String {
    val sym = DecimalFormatSymbols.getInstance()
    val negative = buffer.startsWith("-")
    val body = buffer.removePrefix("-")
    val intPart = body.substringBefore('.')
    val grouped = intPart.toBigIntegerOrNull()?.let { java.text.NumberFormat.getIntegerInstance().format(it) } ?: intPart
    val text = if (body.contains('.')) grouped + sym.decimalSeparator + body.substringAfter('.') else grouped
    val withSymbol = if (position == com.nexo.app.core.model.SymbolPosition.PREFIX) "$symbol $text" else "$text$symbol"
    return if (negative) "-$withSymbol" else withSymbol
}

@Composable
private fun flatFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
)

@Composable
private fun TypeToggles(selected: EntryType, income: Color, expense: Color, enabled: Boolean, highlight: Boolean = true, onSelect: (EntryType) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(EntryType.INCOME to income, EntryType.EXPENSE to expense, EntryType.TRANSFER to MaterialTheme.colorScheme.onSurface).forEach { (t, c) ->
            val sel = highlight && t == selected
            OutlinedButton(
                onClick = { if (enabled) onSelect(t) },
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(if (sel) 2.dp else 1.dp, if (sel) c else MaterialTheme.colorScheme.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) c.copy(alpha = 0.08f) else Color.Transparent),
            ) {
                Text(
                    stringResource(
                        when (t) {
                            EntryType.INCOME -> R.string.income
                            EntryType.EXPENSE -> R.string.expense
                            EntryType.TRANSFER -> R.string.transfer
                        },
                    ),
                    color = if (sel) c else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit,
    bold: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(88.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontSize = if (bold) 20.sp else 16.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            )
            trailing()
        }
        HorizontalDivider(thickness = if (active) 2.dp else 1.dp, color = if (active) accent else MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BottomButtons(
    s: EntryUi,
    color: Color,
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onSkip: () -> Unit,
    review: Boolean,
) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.isEdit && s.viewing && !review) {
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_delete)) }
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = onBookmark, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.bookmark)) }
        } else {
            if (review) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.skip)) }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_delete)) }
            }
            Button(
                onClick = onSave,
                enabled = !s.saving,
                modifier = Modifier.weight(if (review) 1.4f else 2f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            ) {
                Text(if (review) stringResource(R.string.save_n, s.unassignedCount) else stringResource(R.string.action_save))
            }
            if (s.showContinue && !review) {
                OutlinedButton(onClick = onContinue, enabled = !s.saving, modifier = Modifier.weight(1f).height(48.dp)) { Text(stringResource(R.string.action_continue)) }
            }
        }
    }
}
