package com.nexo.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.ui.theme.LocalHeaderColors
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.Space

/** A 40dp round button on a raised fill: back, close and other single-icon actions in headers. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = LocalNexoColors.current.text,
    container: Color = LocalNexoColors.current.raised,
    enabled: Boolean = true,
) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier
            .pressScale(interactions, 0.9f)
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint.copy(alpha = if (enabled) tint.alpha else 0.4f), modifier = Modifier.size(size * 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexoTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    container: Color = LocalHeaderColors.current.container,
    content: Color = LocalHeaderColors.current.content,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (titleContent != null) titleContent()
            else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
        },
        navigationIcon = {
            if (onBack != null) {
                RoundIconButton(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.action_back),
                    onBack,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    tint = content,
                )
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = container,
            scrolledContainerColor = container,
            titleContentColor = content,
            navigationIconContentColor = content,
            actionIconContentColor = content,
        ),
    )
}

/**
 * "‹ title ›" used by every period-based screen. The title slides in the direction of the
 * step; a tappable title shows a small caret.
 */
@Composable
fun PeriodNavigator(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTitleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    color: Color = LocalHeaderColors.current.content,
) {
    val haptics = rememberHaptics()
    var direction by remember { mutableIntStateOf(0) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        StepButton(Icons.Rounded.ChevronLeft, stringResource(R.string.action_previous), color) {
            direction = -1
            haptics.tick()
            onPrevious()
        }
        AnimatedContent(
            targetState = title,
            transitionSpec = { NavMotion.lateral(this, direction) using SizeTransform(clip = false) },
            label = "period",
        ) { t ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t, color = color, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (onTitleClick != null) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, tint = color.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
                }
            }
        }
        StepButton(Icons.Rounded.ChevronRight, stringResource(R.string.action_next), color) {
            direction = 1
            haptics.tick()
            onNext()
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, color: Color, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressScale(interactions, 0.85f)
            .size(40.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = color, modifier = Modifier.size(22.dp))
    }
}

/** A quiet pill with a caret that opens a chooser, e.g. the period mode in a tab header. */
@Composable
fun ModePill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val interactions = remember { MutableInteractionSource() }
    Row(
        modifier
            .pressScale(interactions)
            .height(36.dp)
            .clip(CircleShape)
            .background(colors.raised)
            .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.text)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
    }
}
