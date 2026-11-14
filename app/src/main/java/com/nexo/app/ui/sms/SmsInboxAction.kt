package com.nexo.app.ui.sms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SmsQueueKey

/** Pending count, and whether any of them needs attention (home badge and dot). */
data class QueueBadge(val count: Int = 0, val attention: Boolean = false)

/** The Review Queue's way in from the Transactions header. Shown only while something waits. */
@Composable
fun SmsInboxAction(badge: QueueBadge) {
    val nav = LocalNavigator.current
    val colors = LocalNexoColors.current
    AnimatedVisibility(
        badge.count > 0,
        enter =
            fadeIn(NexoMotion.enter()) + scaleIn(NexoMotion.snappy(), 0.6f),
        exit =
            fadeOut(NexoMotion.exit()) + scaleOut(NexoMotion.exit(), 0.6f),
    ) {
        Box {
            RoundIconButton(
                Icons.Rounded.Inbox,
                pluralStringResource(R.plurals.sms_to_review, badge.count, badge.count),
                { nav.navigate(SmsQueueKey) },
                tint = colors.textSecondary,
                container = Color.Transparent,
            )
            Text(
                if (badge.count > 99) "99+" else badge.count.toString(),
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .defaultMinSize(minWidth = 16.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .padding(horizontal = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (badge.attention) {
                // A hollow dot: something isn't exact or still needs details.
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(x = (-6).dp, y = (-6).dp).size(7.dp)
                        .clip(
                            CircleShape,
                        ).background(colors.background).border(1.5.dp, colors.text, CircleShape),
                )
            }
        }
    }
}
