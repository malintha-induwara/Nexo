package com.nexo.app.ui.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.Space

/** A dropdown menu in the app's look: a rounded panel that floats just under its anchor. */
@Composable
fun NexoMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalNexoColors.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.widthIn(min = 184.dp),
        offset = DpOffset(0.dp, 6.dp),
        shape = NexoShapes.panel,
        containerColor = if (colors.isDark) colors.raised else colors.background,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        // On the dark ground a shadow barely shows, so a hairline lifts the panel instead.
        border = if (colors.isDark) BorderStroke(1.dp, colors.hairline) else null,
        content = content,
    )
}

/** One choice in a [NexoMenu]. [danger] colours a destructive choice. */
@Composable
fun NexoMenuItem(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    val colors = LocalNexoColors.current
    val ink = if (danger) colors.danger else colors.text
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = ink) },
        onClick = onClick,
        leadingIcon = icon?.let {
            {
                Icon(
                    it,
                    null,
                    tint = if (danger) colors.danger else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        contentPadding = PaddingValues(horizontal = Space.l),
    )
}
