package com.nexo.app.ui.sms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.domain.RefData
import com.nexo.app.sms.DenyReason
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.rememberHaptics
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.Space

/** Title and optional hint at the top of an inline sheet. */
@Composable
fun SheetTitle(title: String, hint: String? = null) {
    val colors = LocalNexoColors.current
    Column(Modifier.fillMaxWidth().padding(start = Space.gutter, end = Space.gutter, top = Space.xs, bottom = Space.s)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        if (hint != null) {
            Spacer(Modifier.size(Space.xxs))
            Text(hint, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

/** Visible accounts under their group names; the chosen one carries a check. */
@Composable
fun ColumnScope.AccountPickList(ref: RefData, selectedId: String?, onPick: (String) -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val groups = ref.groups.filter { it.status == GroupStatus.ACTIVE }
        .map { g -> g to ref.pickerAccounts.filter { it.groupId == g.id } }
        .filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Space.xl)) {
        groups.forEach { (group, accounts) ->
            item(key = "g_${group.id}") { SectionHeader(group.name, Modifier.padding(top = Space.s)) }
            items(accounts, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { haptics.tick(); onPick(a.id) }.padding(horizontal = Space.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(a.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = colors.text)
                    if (a.id == selectedId) Icon(Icons.Rounded.Check, null, tint = colors.text, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** NQ-003 optional reason chips on Deny. */
@Composable
fun DenyReasonList(onPick: (DenyReason?) -> Unit) {
    val colors = LocalNexoColors.current
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Space.m)) {
        DenyReason.entries.forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(r) }.padding(horizontal = Space.gutter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(denyLabel(r)), style = MaterialTheme.typography.bodyLarge, color = colors.text)
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { onPick(null) }.padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sms_deny_no_reason), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        }
    }
}

/** A one-off question at the top of a list: card mapping, the history offer, the re-enable hint. */
@Composable
fun SmsPromptCard(
    title: String,
    text: String?,
    primary: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val colors = LocalNexoColors.current
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.s)
            .clip(com.nexo.app.ui.theme.NexoShapes.panel).background(colors.surface).padding(Space.l),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
        if (text != null) {
            Spacer(Modifier.size(Space.xxs))
            Text(text, style = com.nexo.app.ui.theme.NexoType.Meta, color = colors.textSecondary)
        }
        Spacer(Modifier.size(Space.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(
                onClick = onPrimary,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = colors.text, contentColor = colors.background),
            ) { Text(primary, style = MaterialTheme.typography.labelLarge) }
            if (secondary != null && onSecondary != null) {
                androidx.compose.material3.TextButton(onClick = onSecondary, modifier = Modifier.padding(start = Space.s)) {
                    Text(secondary, color = colors.textSecondary)
                }
            }
        }
    }
}
