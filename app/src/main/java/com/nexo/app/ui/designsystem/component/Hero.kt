package com.nexo.app.ui.designsystem.component

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.util.Locale

/**
 * The hide-amounts eye: [enabled] when it is switched on in settings, [hidden] while the large
 * summary figures are blurred. Provided by the app root.
 */
@Immutable
class AmountPrivacy(
    val enabled: Boolean = false,
    val hidden: Boolean = false,
    val onToggle: () -> Unit = {},
)

val LocalAmountPrivacy = compositionLocalOf { AmountPrivacy() }

/** A smaller figure shown under the hero number. */
data class HeroStat(val label: String, val value: Double, val color: Color)

/** A screen's one large number under its label, with a row of smaller labelled figures beneath. */
@Composable
fun HeroSummary(
    label: String,
    value: Double,
    currency: Currency?,
    stats: List<HeroStat>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexoColors.current
    Column(
        modifier.fillMaxWidth().padding(
            start = Space.gutter,
            end = Space.gutter,
            top = Space.xs,
            bottom = Space.m,
        ),
    ) {
        HeroLabel(label)
        HeroAmount(value, currency, Modifier.shrinkToFit(), color = colors.text)
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                stats.forEach { s ->
                    Column {
                        Text(
                            s.label.uppercase(Locale.getDefault()),
                            style = NexoType.Caps,
                            color = colors.textTertiary,
                        )
                        Spacer(Modifier.height(Space.xxs))
                        HeroAmount(
                            s.value,
                            currency,
                            style = NexoType.Amount,
                            color = s.color,
                            minorScale = 0.8f,
                        )
                    }
                }
            }
        }
    }
}

/** The hero's label with the eye button that hides or shows every hero figure in the app. */
@Composable
fun HeroLabel(label: String, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    val privacy = LocalAmountPrivacy.current
    val haptics = rememberHaptics()
    val description = stringResource(
        if (privacy.hidden) R.string.show_amounts else R.string.hide_amounts,
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        if (!privacy.enabled) return@Row
        // Small on screen; Compose widens the touch area of small clickables to the minimum size.
        Box(
            Modifier
                .padding(start = Space.xs)
                .size(28.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) {
                    haptics.toggle(!privacy.hidden)
                    privacy.onToggle()
                }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(privacy.hidden, animationSpec = NexoMotion.standard(), label = "eye") { h ->
                Icon(
                    if (h) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** A hero figure that rolls like [RollingAmount], blurred in place while amounts are hidden. */
@Composable
fun HeroAmount(
    value: Double,
    currency: Currency?,
    modifier: Modifier = Modifier,
    style: TextStyle = NexoType.Hero,
    color: Color = LocalNexoColors.current.text,
    minorScale: Float = 0.5f,
) {
    val hidden = LocalAmountPrivacy.current.hidden
    val amount by animateFloatAsState(
        if (hidden) 1f else 0f,
        NexoMotion.standard(),
        label = "heroBlur",
    )
    val description = stringResource(R.string.amount_hidden)
    RollingAmount(
        value,
        currency,
        modifier
            .privacyBlur(amount, style.fontSize, color)
            .then(
                if (hidden) {
                    Modifier.clearAndSetSemantics { contentDescription = description }
                } else {
                    Modifier
                },
            ),
        style = style,
        color = color,
        minorScale = minorScale,
    )
}

/**
 * Blurs the content by [amount] (0 to 1) without changing its size, so nothing around it moves.
 * The radius grows with the text so even the hero digits can't be read. Blur needs Android 12;
 * older versions fade the figure out over a soft bar instead.
 */
private fun Modifier.privacyBlur(amount: Float, fontSize: TextUnit, color: Color): Modifier {
    if (amount <= 0f) return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radius = (fontSize.value * 0.3f).coerceAtLeast(6f)
        blur((radius * amount).dp, BlurredEdgeTreatment.Unbounded)
    } else {
        drawBehind {
            val h = size.height * 0.55f
            drawRoundRect(
                color.copy(alpha = color.alpha * 0.22f * amount),
                topLeft = Offset(0f, (size.height - h) / 2),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2),
            )
        }.graphicsLayer { alpha = 1f - amount }
    }
}
