package com.nexo.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.nexo.app.R
import com.nexo.app.core.common.money.AmountParts
import com.nexo.app.core.model.Currency
import com.nexo.app.ui.designsystem.component.AmountText
import com.nexo.app.ui.designsystem.component.IconTile
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Sizes
import com.nexo.app.ui.designsystem.theme.Space

@Composable
fun RepeatPreviewRow(
    p: RepeatPreview,
    main: Currency,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalNexoColors.current
    Row(
        modifier.fillMaxWidth().heightIn(
            min = Sizes.row,
        ).clickable(onClick = onClick).padding(horizontal = Space.gutter, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.Repeat, colors.textSecondary)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.repeat), style = NexoType.RowTitle, color = colors.text)
            val detail = buildList {
                add(p.dates.joinToString(", ") { "${it.monthValue}/${it.dayOfMonth}" })
                if (p.names.isNotEmpty()) add(p.names.joinToString(", "))
            }.joinToString(" · ")
            Text(
                detail,
                style = NexoType.Meta,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.m))
        AmountText(p.amount, main, color = colors.textSecondary, sign = AmountParts.Sign.NONE)
    }
}
