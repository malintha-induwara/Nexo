package com.nexo.app.ui.transactions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.Memo
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.Formats
import com.nexo.app.ui.designsystem.theme.LocalIsDark
import com.nexo.app.ui.designsystem.theme.NexoPalette
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteContent(
    memos: List<Memo>,
    saved: String?,
    onSavedShown: () -> Unit,
    bottom: Dp,
    onOpenMemo: (memoId: String) -> Unit,
) {
    val dark = LocalIsDark.current
    if (memos.isEmpty()) {
        EmptyState(stringResource(R.string.no_memos))
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var lastDate: String? = null
        memos.forEach { m ->
            if (!m.pinned && m.date != lastDate) {
                lastDate = m.date
                val d = LocalDate.parse(m.date)
                item(key = "d_${m.date}") {
                    Text(
                        "${Formats.monthDay(d)} (${Formats.weekdayShort(d)})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }
            item(key = m.id) {
                val flash = remember { Animatable(0f) }
                LaunchedEffect(saved) {
                    if (saved == m.id) {
                        flash.animateTo(1f)
                        flash.animateTo(0f)
                        onSavedShown()
                    }
                }
                Card(
                    onClick = { onOpenMemo(m.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = NexoPalette.memo(m.color, dark),
                    ),
                    modifier = Modifier.fillMaxWidth().border(
                        if (flash.value > 0f) {
                            2.dp
                        } else {
                            0.dp
                        },
                        MaterialTheme.colorScheme.primary.copy(alpha = flash.value),
                        CardDefaults.shape,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (m.pinned) {
                                Icon(
                                    Icons.Outlined.PushPin,
                                    null,
                                    Modifier.size(16.dp).padding(end = 4.dp),
                                )
                            }
                            if (m.title.isNotBlank()) {
                                Text(
                                    m.title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (m.content.isNotBlank()) {
                            Text(
                                m.content.trimEnd('\n'),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
