package com.nexo.app.ui.entry

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.EntryType
import com.nexo.app.core.model.TxKind
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.SegmentedPill
import com.nexo.app.ui.designsystem.theme.LocalMoneyColors
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.EntryMode
import java.util.Locale

/** Close, the type switch, and revert or bookmarks; under it, the kind of entry when it is not a plain one. */
@Composable
internal fun EntryTopBar(
    s: EntryForm,
    device: DeviceSettings?,
    typeColor: Color,
    actions: EntryActions,
    dialogs: EntryDialogState,
) {
    val money = LocalMoneyColors.current
    val modeLabel = when {
        s.balanceKind == TxKind.BALANCE_INCREASE -> stringResource(R.string.income_balance)
        s.balanceKind == TxKind.BALANCE_DECREASE -> stringResource(R.string.expense_balance)
        s.mode == EntryMode.PAY_CARD -> stringResource(R.string.card_payment)
        s.repeatMode -> stringResource(R.string.repeat)
        else -> null
    }
    // Top: close, type, bookmarks / revert.
    Row(
        Modifier.fillMaxWidth().height(60.dp).padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            Icons.Rounded.Close,
            stringResource(R.string.action_back),
            actions.close,
        )
        Box(
            Modifier.weight(1f).padding(horizontal = Space.s),
            contentAlignment = Alignment.Center,
        ) {
            SegmentedPill(
                options = listOf(EntryType.INCOME, EntryType.EXPENSE, EntryType.TRANSFER),
                selected = s.type,
                onSelect = { t ->
                    if (t == EntryType.TRANSFER && device?.transferGuideShown == false) {
                        dialogs.transferGuide = true
                    }
                    actions.setType(t)
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
                accent = { t ->
                    if (t == EntryType.INCOME) {
                        money.income
                    } else if (t == EntryType.EXPENSE) {
                        money.expense
                    } else {
                        null
                    }
                },
                enabled = s.mode != EntryMode.PAY_CARD || s.isBalance,
                showSelection = !s.isBalance,
                segmentPadding = 12.dp,
                modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
            )
        }
        Box(Modifier.size(40.dp)) {
            when {
                s.isEdit && !s.viewing -> RoundIconButton(
                    Icons.AutoMirrored.Rounded.Undo,
                    stringResource(R.string.revert),
                    actions.revert,
                )

                (
                    s.mode == EntryMode.NEW || s.mode == EntryMode.PAY_CARD ||
                        s.mode == EntryMode.FAVORITE
                    ) &&
                    s.originalId == null ->
                    RoundIconButton(
                        Icons.Rounded.StarOutline,
                        stringResource(R.string.bookmarks),
                        actions.openBookmarks,
                    )
            }
        }
    }
    modeLabel?.let {
        Text(
            it.uppercase(Locale.getDefault()),
            style = NexoType.Caps,
            color = typeColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs),
        )
    }
}
