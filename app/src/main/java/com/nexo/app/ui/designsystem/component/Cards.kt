package com.nexo.app.ui.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType

/**
 * A rounded card for something that gathers transactions under it: a month, a year, an account.
 * Single transactions stay plain rows. [highlighted] marks the current period.
 * [interactions] lets a caller drive the press feedback from one part of the card, e.g. its header.
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactions: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNexoColors.current
    val container by animateColorAsState(
        if (highlighted) colors.primarySoft else colors.surface,
        NexoMotion.standard(),
        label = "groupCard",
    )
    Column(
        modifier
            .pressScale(interactions, 0.98f)
            .fillMaxWidth()
            .clip(NexoShapes.panel)
            .background(container)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactions,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

/** An income or expense figure in its colour; zero is dimmed so the periods with activity stand out. */
@Composable
fun FlowAmount(
    value: Double,
    currency: Currency?,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Amount,
) {
    AmountText(
        value,
        currency,
        modifier,
        style = style,
        color = if (value == 0.0) {
            LocalNexoColors.current.textTertiary
        } else {
            color
        },
    )
}
