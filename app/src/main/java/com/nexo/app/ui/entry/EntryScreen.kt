package com.nexo.app.ui.entry

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.designsystem.asString
import com.nexo.app.ui.designsystem.component.InlineBottomSheet
import com.nexo.app.ui.designsystem.component.PadKey
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.navigation.AccountFormKey
import com.nexo.app.ui.navigation.AccountListKey
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.NavResult
import com.nexo.app.ui.navigation.Navigator
import com.nexo.app.ui.navigation.ReceiveNavResult
import com.nexo.app.ui.navigation.SubCurrenciesKey
import java.time.LocalTime

@Composable
fun EntryRoute(key: EntryKey) {
    val vm =
        hiltViewModel<EntryViewModel, EntryViewModel.Factory>(creationCallback = { it.create(key) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val dialogs = rememberEntryDialogState()
    val paymentNote = stringResource(R.string.payment)
    LaunchedEffect(Unit) { vm.initialize(EntryTexts(paymentNote = paymentNote)) }
    state.userMessage?.let { message ->
        val text = message.text.asString()
        LaunchedEffect(message.id) {
            haptics.reject()
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            vm.onUserMessageShown(message.id)
        }
    }
    ReceiveNavResult(nav.results, NavResult.FavoritePicked::class) { vm.applyFavorite(it.id) }
    // Results go out before the form closes, while this screen can still deliver them.
    LaunchedEffect(state.results, state.navigation) {
        if (state.results.isNotEmpty()) {
            state.results.forEach(nav.results::post)
            vm.onResultsDelivered(state.results)
        }
        when (val target = state.navigation) {
            EntryNavigation.Close -> nav.back()

            is EntryNavigation.Open -> if (target.replace) {
                nav.replace(target.key)
            } else {
                nav.navigate(target.key)
            }

            null -> return@LaunchedEffect
        }
        vm.onNavigationHandled()
    }
    val scanner = rememberReceiptScanner(
        onScanned = vm::scanReceipt,
        onPicked = vm::scanPickedPhoto,
        onUnavailable = { dialogs.scanMenu = true },
    )
    val actions = remember(vm, nav, scanner) { entryActions(vm, nav, scanner) }
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    EntryScreen(
        state = state,
        actions = actions,
        dialogs = dialogs,
        asDrawer = key.fromAdd,
        reviewingUnassigned = key.reviewUnassigned,
        hasCamera = hasCamera,
    )
}

private fun entryActions(vm: EntryViewModel, nav: Navigator, scanner: ReceiptScanner) =
    EntryActions(
        close = nav::back,
        openBookmarks = { nav.navigate(BookmarksKey(pickForForm = true)) },
        editAccounts = { nav.navigate(AccountListKey()) },
        addAccount = { nav.navigate(AccountFormKey()) },
        editCategories = { nav.navigate(CategoryListKey(it)) },
        openSubCurrencies = { nav.navigate(SubCurrenciesKey) },
        scanReceipt = scanner::scan,
        takePhoto = scanner::takePhoto,
        pickPhoto = scanner::pickPhoto,
        dismissScan = vm::dismissScan,
        applyReceipt = vm::applyReceipt,
        setType = vm::setType,
        revert = vm::revert,
        enterEdit = vm::enterEdit,
        openPanel = vm::openPanel,
        closePanel = vm::closePanel,
        setNote = vm::setNote,
        setDescription = vm::setDescription,
        swap = vm::swap,
        selectAccount = vm::selectAccount,
        selectCategory = vm::selectCategory,
        setGroupedPicker = vm::setGroupedPicker,
        setDate = vm::setDate,
        setTime = vm::setTime,
        padKey = vm::padKey,
        padDone = vm::padDone,
        setPadCurrency = vm::setPadCurrency,
        calculatorResult = vm::calculatorResult,
        setMainAmount = vm::setMainAmount,
        setRate = vm::setRate,
        setFeeExpanded = vm::setFeeExpanded,
        setRepeat = vm::setRepeat,
        setRepeatEnd = vm::setRepeatEnd,
        setInstallments = vm::setInstallments,
        clearRepInst = vm::clearRepInst,
        setTags = vm::setTags,
        removeTag = vm::removeTag,
        tags = vm.tagActions,
        exportPhoto = vm::exportPhoto,
        onPhotoExportShown = vm::onPhotoExportShown,
        removePhoto = vm::removePhoto,
        save = vm::save,
        confirmRepeatSave = vm::confirmRepeatSave,
        dismissRepeatConfirm = vm::dismissRepeatConfirm,
        setAskDelete = vm::setAskDelete,
        delete = vm::delete,
        copy = vm::copy,
        bookmark = vm::bookmark,
        skip = vm::skip,
        denySms = vm::denySms,
        markPhotoNoticeShown = vm::markPhotoNoticeShown,
        markTransferGuideShown = vm::markTransferGuideShown,
    )

/**
 * The entry form. [asDrawer] when it opens over another screen from an add button;
 * [reviewingUnassigned] when it steps through rows missing a category or account.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryScreen(
    state: EntryUiState,
    actions: EntryActions,
    dialogs: EntryDialogState,
    asDrawer: Boolean,
    reviewingUnassigned: Boolean,
    hasCamera: Boolean,
) {
    val s = state.form
    val ref = state.ref
    val device = state.device
    val money = LocalMoneyColors.current
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val noteFocus = remember { FocusRequester() }

    var shake by remember { mutableIntStateOf(0) }

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
    BackHandler(enabled = s.panel != Panel.NONE) { actions.closePanel() }

    val typeColor = when {
        s.type == EntryType.INCOME -> money.income
        s.type == EntryType.EXPENSE -> money.expense
        else -> colors.text
    }
    val txCurrency = ref.currency(s.currencyId)

    /** Keypad input with a shake and a reject buzz when the key cannot change the amount. */
    fun pressKey(k: PadKey) {
        val r = s.pad.press(k)
        if (r.input == s.pad && !r.negateExisting && !r.clearField) {
            shake++
            haptics.reject()
        }
        actions.padKey(k)
    }

    // An edited entry loads from the database a moment after the screen appears; fade it in rather than pop.
    val reveal by animateFloatAsState(if (s.loaded) 1f else 0f, tween(180), label = "entryReveal")
    // As a drawer (opened from an add button) the sheet supplies the background and already sits below the status bar.
    Box(
        if (asDrawer) {
            Modifier.fillMaxSize()
        } else {
            Modifier.fillMaxSize().background(colors.background).statusBarsPadding()
        },
    ) {
        if (!s.loaded) return@Box
        Column(
            Modifier.fillMaxSize().graphicsLayer { alpha = reveal }.imePadding()
                .onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown ||
                        (s.panel != Panel.AMOUNT && s.panel != Panel.FEE)
                    ) {
                        return@onPreviewKeyEvent false
                    }
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

                        Key.Enter, Key.NumPadEnter -> {
                            actions.padDone()
                            return@onPreviewKeyEvent true
                        }

                        else -> return@onPreviewKeyEvent false
                    }
                    pressKey(k)
                    true
                },
        ) {
            EntryTopBar(s, device, typeColor, actions, dialogs)

            EntryFields(
                state,
                actions,
                dialogs,
                typeColor,
                noteFocus,
                shake,
                hasCamera,
                Modifier.weight(1f),
            )

            EntryBottomArea(state, actions, dialogs, typeColor, reviewingUnassigned, ::pressKey)
        }

        // Scan receipt's confirm sheet. The last reading stays drawn while the sheet slides away.
        val shownScan = remember { arrayOfNulls<ScanUi>(1) }
        if (s.scan != null) shownScan[0] = s.scan
        InlineBottomSheet(
            visible = s.scan != null,
            onDismiss = actions.dismissScan,
            heightFraction = 0.62f,
        ) {
            shownScan[0]?.let { scan ->
                ReceiptSheetContent(
                    scan,
                    txCurrency,
                    noteEmpty = s.note.isBlank(),
                    onUse = actions.applyReceipt,
                    onCancel = actions.dismissScan,
                )
            }
        }
    }

    EntryDialogs(state, actions, dialogs)
}

internal val previewEntryState
    get() = EntryUiState(
        form = EntryForm(
            loaded = true,
            date = PreviewData.today,
            time = LocalTime.of(13, 5),
            accountId = "cash",
            categoryId = "food",
            amount = 1200.0,
            currencyId = "LKR",
            feeCurrencyId = "LKR",
            note = "Lunch",
            tagIds = listOf("work"),
            filledOnce = true,
        ),
        ref = PreviewData.ref,
        device = PreviewData.device,
        tags = PreviewData.tags,
    )

@Preview
@Composable
private fun EntryScreenPreview() = NexoPreview {
    EntryScreen(
        state = previewEntryState,
        actions = EntryActions(),
        dialogs = rememberEntryDialogState(),
        asDrawer = false,
        reviewingUnassigned = false,
        hasCamera = true,
    )
}

@Preview
@Composable
private fun EntryScreenKeypadPreview() = NexoPreview(dark = true) {
    val state = previewEntryState
    EntryScreen(
        state = state.copy(form = state.form.copy(panel = Panel.AMOUNT, type = EntryType.INCOME)),
        actions = EntryActions(),
        dialogs = rememberEntryDialogState(),
        asDrawer = false,
        reviewingUnassigned = false,
        hasCamera = true,
    )
}
