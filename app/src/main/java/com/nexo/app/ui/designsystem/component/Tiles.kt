package com.nexo.app.ui.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.ui.designsystem.theme.Figtree
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.Radius
import com.nexo.app.ui.designsystem.theme.Sizes

/** A rounded square tinted with [hue] at low strength. Text inside is sized from the tile, not the font scale. */
@Composable
fun Tile(
    hue: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.tile,
    over: Color = LocalNexoColors.current.background,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalNexoColors.current
    val corner = if (size >= Sizes.tile) Radius.tile else (size.value * Radius.tile.value / Sizes.tile.value).dp
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(colors.tint(hue, over)),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Category identity: the emoji when there is one, otherwise the monogram in the hue. Decorative for accessibility. */
@Composable
fun CategoryTile(
    emoji: String?,
    monogram: String,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.tile,
    over: Color = LocalNexoColors.current.background,
) {
    val density = LocalDensity.current
    Tile(hue, modifier.clearAndSetSemantics { }, size, over) {
        if (!emoji.isNullOrEmpty()) {
            val px = with(density) { (size * 0.5f).toPx() }
            Text(
                emoji,
                style = TextStyle(fontSize = with(density) { (px / fontScale).toSp() }, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        } else {
            val px = with(density) { (size * 0.4f).toPx() }
            Text(
                monogram,
                style = TextStyle(fontFamily = Figtree, fontWeight = FontWeight.SemiBold, fontSize = with(density) { (px / fontScale).toSp() }, color = hue),
                maxLines = 1,
            )
        }
    }
}

/** A tile carrying an icon, for rows that are not a category (transfers, adjustments, carry-over). */
@Composable
fun IconTile(
    icon: ImageVector,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.tile,
    tint: Color = hue,
    over: Color = LocalNexoColors.current.background,
) {
    Tile(hue, modifier.clearAndSetSemantics { }, size, over) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
