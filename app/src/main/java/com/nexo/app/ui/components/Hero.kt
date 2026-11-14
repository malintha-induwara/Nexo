package com.nexo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nexo.app.core.database.entity.CurrencyEntity
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoType
import com.nexo.app.ui.theme.Space
import java.util.Locale

/** A smaller figure shown under the hero number. */
data class HeroStat(val label: String, val value: Double, val color: Color)

/** A screen's one large number under its label, with a row of smaller labelled figures beneath. */
@Composable
fun HeroSummary(label: String, value: Double, currency: CurrencyEntity?, stats: List<HeroStat>, modifier: Modifier = Modifier) {
    val colors = LocalNexoColors.current
    Column(modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.m)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        RollingAmount(value, currency, Modifier.shrinkToFit(), color = colors.text)
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
                stats.forEach { s ->
                    Column {
                        Text(s.label.uppercase(Locale.getDefault()), style = NexoType.Caps, color = colors.textTertiary)
                        Spacer(Modifier.height(Space.xxs))
                        RollingAmount(s.value, currency, style = NexoType.Amount, color = s.color, minorScale = 0.8f)
                    }
                }
            }
        }
    }
}
