package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Sizes
import com.nexo.app.ui.designsystem.theme.Space
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * In-app number pad: a 4×4 grid of rounded keys on a surface sheet. Every key gives a
 * haptic tick and a press scale; Done carries [accent] (the entry type's colour).
 */
@Composable
fun NumberPad(
    title: String,
    onKey: (PadKey) -> Unit,
    onDone: () -> Unit,
    onCalculator: (() -> Unit)?,
    modifier: Modifier = Modifier,
    descending: Boolean = false,
    decimals: Int = 2,
    signKey: Boolean = true,
    currencies: List<Currency> = emptyList(),
    selectedCurrencyId: String? = null,
    onCurrency: ((Currency) -> Unit)? = null,
    onGlobe: (() -> Unit)? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val colors = LocalNexoColors.current
    val separator = DecimalFormatSymbols.getInstance().decimalSeparator.toString()
    Column(
        modifier
            .fillMaxWidth()
            .clip(NexoShapes.sheet)
            .background(colors.surface)
            .padding(start = Space.m, end = Space.m, top = Space.s, bottom = Space.s),
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(Locale.getDefault()),
                style = NexoType.Caps,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = Space.s, end = Space.m),
            )
            if (currencies.size > 1 && onCurrency != null) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    currencies.forEach { c ->
                        CurrencyPill(
                            c.isoCode?.takeIf { it.isNotEmpty() } ?: c.symbol,
                            c.id == selectedCurrencyId,
                        ) { onCurrency(c) }
                    }
                }
            } else {
                Box(Modifier.weight(1f))
            }
            if (onGlobe != null) {
                RoundIconButton(
                    Icons.Outlined.Language,
                    stringResource(R.string.sub_currency_setting),
                    onGlobe,
                    size = 36.dp,
                    tint = colors.textSecondary,
                    container = Color.Transparent,
                )
            }
            RoundIconButton(
                Icons.Rounded.KeyboardArrowDown,
                stringResource(R.string.action_close),
                onDone,
                size = 36.dp,
                tint = colors.textSecondary,
                container = Color.Transparent,
            )
        }
        val digitRows = if (descending) {
            listOf(listOf(7, 8, 9), listOf(4, 5, 6), listOf(1, 2, 3))
        } else {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
        }
        Column(
            Modifier.padding(top = Space.xs),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            PadRow {
                digitRows[0].forEach { d ->
                    PadKeyButton(d.toString()) { onKey(PadKey.entries[d]) }
                }
                PadIconKey(stringResource(R.string.action_delete), {
                    Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = colors.textSecondary)
                }) { onKey(PadKey.BACKSPACE) }
            }
            PadRow {
                digitRows[1].forEach { d ->
                    PadKeyButton(d.toString()) { onKey(PadKey.entries[d]) }
                }
                if (signKey) {
                    PadKeyButton("±", function = true) {
                        onKey(PadKey.SIGN)
                    }
                } else {
                    PadKeyButton("C", function = true) { onKey(PadKey.CLEAR) }
                }
            }
            PadRow {
                digitRows[2].forEach { d ->
                    PadKeyButton(d.toString()) { onKey(PadKey.entries[d]) }
                }
                if (onCalculator != null) {
                    PadIconKey(stringResource(R.string.calculator), {
                        Icon(Icons.Rounded.Calculate, null, tint = colors.textSecondary)
                    }) { onCalculator() }
                } else {
                    Box(Modifier.weight(1f))
                }
            }
            PadRow {
                PadKeyButton("00") { onKey(PadKey.DOUBLE_ZERO) }
                PadKeyButton("0") { onKey(PadKey.D0) }
                if (decimals > 0) {
                    PadKeyButton(separator) { onKey(PadKey.DECIMAL) }
                } else {
                    PadKeyButton("000") { onKey(PadKey.TRIPLE_ZERO) }
                }
                PadKeyButton(
                    stringResource(R.string.action_done),
                    background = accent,
                    content = MaterialTheme.colorScheme.onPrimary,
                    label = true,
                ) {
                    onDone()
                }
            }
        }
    }
}

@Composable
private fun CurrencyPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val bg by animateColorAsState(
        if (selected) colors.primarySoft else colors.raised,
        NexoMotion.standard(),
        label = "currencyBg",
    )
    val fg by animateColorAsState(
        if (selected) colors.primaryText else colors.textSecondary,
        NexoMotion.standard(),
        label = "currencyFg",
    )
    Box(
        Modifier.height(30.dp).clip(CircleShape).background(bg).clickable(role = Role.RadioButton) {
            haptics.tick()
            onClick()
        }.padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

@Composable
private fun PadRow(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.fillMaxWidth().height(Sizes.keypadKey),
    horizontalArrangement = Arrangement.spacedBy(Space.s),
    content = content,
)

@Composable
private fun RowScope.PadSurface(
    description: String?,
    background: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed && background == colors.raised) colors.raisedHigh else background,
        NexoMotion.press(),
        label = "keyFill",
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressScale(interactions, 0.93f)
            .clip(NexoShapes.control)
            .background(fill)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button) {
                haptics.key()
                onClick()
            }
            .then(
                if (description != null) {
                    Modifier.semantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RowScope.PadKeyButton(
    text: String,
    background: Color = LocalNexoColors.current.raised,
    content: Color = LocalNexoColors.current.text,
    function: Boolean = false,
    label: Boolean = false,
    onClick: () -> Unit,
) {
    PadSurface(null, background, onClick) {
        Text(
            text,
            style = if (label) MaterialTheme.typography.titleMedium else NexoType.Keypad,
            color = if (function) LocalNexoColors.current.textSecondary else content,
        )
    }
}

@Composable
private fun RowScope.PadIconKey(
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    PadSurface(description, LocalNexoColors.current.raised, onClick) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
    }
}
