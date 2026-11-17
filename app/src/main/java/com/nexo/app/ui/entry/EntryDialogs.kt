package com.nexo.app.ui.entry

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountFormatter
import com.nexo.app.core.common.money.Conversion
import com.nexo.app.core.common.money.Money
import com.nexo.app.core.model.RepeatReflect
import com.nexo.app.core.model.RepeatType
import com.nexo.app.ui.designsystem.component.AmountSheet
import com.nexo.app.ui.designsystem.component.CalculatorDialog
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.NexoDatePickerDialog
import com.nexo.app.ui.designsystem.component.NexoTimePickerDialog
import com.nexo.app.ui.designsystem.component.rememberHaptics

@Composable
internal fun EntryDialogs(state: EntryUiState, actions: EntryActions, dialogs: EntryDialogState) {
    val s = state.form
    val ref = state.ref
    val device = state.device
    val tags = state.tags
    val resources = LocalResources.current
    val haptics = rememberHaptics()
    val txCurrency = ref.currency(s.currencyId)
    if (dialogs.date) {
        NexoDatePickerDialog(s.date, onPick = actions.setDate, onDismiss = {
            dialogs.date = false
        })
    }
    if (dialogs.time) {
        NexoTimePickerDialog(s.time, onPick = actions.setTime, onDismiss = {
            dialogs.time = false
        })
    }
    if (dialogs.repeatEnd) {
        NexoDatePickerDialog(s.repeatEnd ?: s.date.plusYears(1), onPick = {
            actions.setRepeatEnd(if (it.isBefore(s.date)) s.date else it)
        }, onDismiss = {
            dialogs.repeatEnd = false
        })
    }
    if (dialogs.calculator) {
        CalculatorDialog(
            if (s.panel == Panel.FEE) {
                s.feeAmount
            } else {
                s.amount
            },
            onResult = actions.calculatorResult,
            onDismiss = {
                dialogs.calculator = false
            },
        )
    }
    if (dialogs.repeatList) {
        ChoiceDialog(
            title = stringResource(R.string.repeat),
            options = RepeatType.pickerOrder,
            selected = s.repeat,
            label = { repeatLabel(it) },
            onSelect = actions.setRepeat,
            onDismiss = { dialogs.repeatList = false },
        )
    }
    if (dialogs.installments) {
        AmountSheet(
            title = stringResource(R.string.monthly_installment_plan),
            initial = s.installments.takeIf { it > 0 }?.toDouble(),
            decimals = 0,
            allowSign = false,
            calculator = false,
            maxLength = 3,
            validate = { (it ?: 0.0) <= 600 },
            format = { pluralStringResourceText(resources, it.toInt()) },
            onConfirm = { actions.setInstallments((it ?: 0.0).toInt()) },
            onDismiss = { dialogs.installments = false },
        )
    }
    if (dialogs.rateEdit != 0) {
        val amount = s.amount ?: 0.0
        val rate = s.rate ?: Conversion.effectiveRate(txCurrency)
        if (dialogs.rateEdit == 1) {
            AmountSheet(
                title = stringResource(R.string.main_currency_amount),
                initial = Money.round(amount * rate, ref.mainCurrency.decimals),
                decimals = ref.mainCurrency.decimals,
                allowSign = false,
                onConfirm = { it?.let(actions.setMainAmount) },
                onDismiss = { dialogs.rateEdit = 0 },
            )
        } else {
            AmountSheet(
                title = stringResource(R.string.exchange_rate),
                initial = rate,
                decimals = 10,
                allowSign = false,
                format = { AmountFormatter.rate(it) },
                onConfirm = { it?.takeIf { v -> v > 0 }?.let(actions.setRate) },
                onDismiss = { dialogs.rateEdit = 0 },
            )
        }
    }
    if (dialogs.tags) {
        TagSelectSheet(
            tags = tags,
            initial = s.tagIds,
            dialog = state.tagDialog,
            actions = actions.tags,
            onDone = actions.setTags,
            onDismiss = { dialogs.tags = false },
        )
    }
    if (dialogs.viewerIndex >= 0) {
        PhotoViewer(
            s.photos,
            dialogs.viewerIndex,
            photoFiles = state.photoFiles,
            exported = state.photoExported,
            onExport = actions.exportPhoto,
            onExportShown = actions.onPhotoExportShown,
            onDismiss = { dialogs.viewerIndex = -1 },
        )
    }
    if (dialogs.photoNotice) {
        AlertDialog(
            onDismissRequest = { dialogs.photoNotice = false },
            text = { Text(stringResource(R.string.photo_notice)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogs.photoNotice = false
                    actions.markPhotoNoticeShown()
                    actions.scanReceipt()
                }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    if (dialogs.transferGuide) {
        AlertDialog(
            onDismissRequest = {
                dialogs.transferGuide = false
                actions.markTransferGuideShown()
            },
            title = { Text(stringResource(R.string.transfer)) },
            text = { Text(stringResource(R.string.transfer_guide)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogs.transferGuide = false
                    actions.markTransferGuideShown()
                }) { Text(stringResource(R.string.action_ok)) }
            },
        )
    }
    if (s.confirmRepeat) {
        ConfirmDialog(
            message =
                stringResource(
                    if (ref.settings.repeatReflect == RepeatReflect.ON_DATE) {
                        R.string.repeat_reflect_on_date
                    } else {
                        R.string.repeat_reflect_first_day
                    },
                ) +
                    "\n\n" + stringResource(R.string.repeat_save_question),
            onConfirm = actions.confirmRepeatSave,
            onDismiss = actions.dismissRepeatConfirm,
        )
    }
    if (s.askDelete) {
        if (s.installmentInfo != null) {
            AlertDialog(
                onDismissRequest = { actions.setAskDelete(false) },
                text = { Text(stringResource(R.string.confirm_delete)) },
                confirmButton = {
                    Row {
                        TextButton(onClick = {
                            actions.setAskDelete(false)
                            haptics.confirm()
                            actions.delete(true)
                        }) { Text(stringResource(R.string.delete_all)) }
                        TextButton(onClick = {
                            actions.setAskDelete(false)
                            haptics.confirm()
                            actions.delete(false)
                        }) { Text(stringResource(R.string.action_delete)) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        actions.setAskDelete(false)
                    }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        } else {
            ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
                haptics.confirm()
                actions.delete(false)
            }, onDismiss = { actions.setAskDelete(false) })
        }
    }
    if (dialogs.copy) {
        var today by remember { mutableStateOf(device?.copyWithToday ?: true) }
        AlertDialog(
            onDismissRequest = { dialogs.copy = false },
            title = { Text(stringResource(R.string.copy)) },
            text = {
                Column {
                    listOf(
                        true to R.string.copy_today,
                        false to R.string.copy_original,
                    ).forEach { (v, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                today = v
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = today == v, onClick = null)
                            Text(stringResource(label), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dialogs.copy = false
                    actions.copy(today)
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogs.copy = false
                }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

private fun pluralStringResourceText(resources: Resources, n: Int): String =
    resources.getQuantityString(R.plurals.n_months, n, n)
