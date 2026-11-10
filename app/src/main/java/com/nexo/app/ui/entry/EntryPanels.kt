package com.nexo.app.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.database.entity.AccountEntity
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.domain.RefData

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        extra()
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.action_close)) }
    }
}

@Composable
private fun GridCell(text: String, selected: Boolean, accent: Color, dimmed: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.padding(3.dp).fillMaxWidth().height(48.dp)
            .background(if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 6.dp),
            color = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Account selection panel (TX-005): flat grid or grouped two-pane. */
@Composable
fun AccountPanel(
    title: String,
    ref: RefData,
    selectedId: String?,
    grouped: Boolean,
    accent: Color,
    onToggleGrouped: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        PanelHeader(title, onClose) {
            IconButton(onClick = onToggleGrouped) {
                Icon(if (grouped) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda, stringResource(R.string.toggle_grouping))
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
        }
        val visible = ref.orderedAccounts().filter { a ->
            val g = ref.groupById[a.groupId]
            g != null && g.status == com.nexo.app.core.model.GroupStatus.ACTIVE && (a.status == AccountStatus.ACTIVE || (grouped && a.status == AccountStatus.HIDDEN))
        }
        if (!grouped) {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(horizontal = 6.dp)) {
                items(visible.filter { it.status == AccountStatus.ACTIVE }, key = { it.id }) { a ->
                    GridCell(a.name, a.id == selectedId, accent) { onSelect(a.id) }
                }
                item { AddCell(onAdd) }
            }
        } else {
            val groups = ref.groups.filter { g -> visible.any { it.groupId == g.id } }
            var group by remember(selectedId) { mutableStateOf(ref.accountById[selectedId]?.groupId ?: groups.firstOrNull()?.id) }
            Row(Modifier.fillMaxHeight()) {
                LazyColumn(Modifier.weight(1f)) {
                    items(groups, key = { it.id }) { g ->
                        ListCell(g.name, g.id == group, accent, chevron = true) { group = g.id }
                    }
                }
                LazyColumn(Modifier.weight(1.4f)) {
                    val inGroup: List<AccountEntity> = visible.filter { it.groupId == group }.sortedBy { if (it.status == AccountStatus.HIDDEN) 1 else 0 }
                    items(inGroup, key = { it.id }) { a ->
                        ListCell(a.name, a.id == selectedId, accent, dimmed = a.status == AccountStatus.HIDDEN) { onSelect(a.id) }
                    }
                    item { ListCell("+ " + stringResource(R.string.add_account), false, accent) { onAdd() } }
                }
            }
        }
    }
}

@Composable
private fun AddCell(onAdd: () -> Unit) {
    Box(
        Modifier.padding(3.dp).fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Add, stringResource(R.string.add_account), tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun ListCell(text: String, selected: Boolean, accent: Color, chevron: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dimmed) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

/** Category selection panel (TX-006): main grid, or main/sub two-pane when subcategories are on. */
@Composable
fun CategoryPanel(
    kind: CategoryKind,
    ref: RefData,
    selectedId: String?,
    accent: Color,
    onSelect: (String) -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().height(300.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        PanelHeader(stringResource(R.string.category), onClose) {
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
        }
        val mains = ref.mainCategories(kind)
        if (!ref.settings.subcategories) {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(horizontal = 6.dp)) {
                items(mains, key = { it.id }) { c -> GridCell(c.name, c.id == ref.mainCategoryId(selectedId), accent) { onSelect(c.id) } }
            }
        } else {
            val selectedMain = ref.mainCategoryId(selectedId)
            var open by remember(selectedId) { mutableStateOf(selectedMain) }
            Row(Modifier.fillMaxHeight()) {
                LazyColumn(Modifier.weight(1f)) {
                    items(mains, key = { it.id }) { c ->
                        val subs = ref.subCategories(c.id)
                        ListCell(c.name, c.id == open, accent, chevron = subs.isNotEmpty()) {
                            if (subs.isEmpty() || open == c.id) onSelect(c.id) else open = c.id
                        }
                    }
                }
                val subs: List<CategoryEntity> = open?.let { ref.subCategories(it) }.orEmpty()
                if (subs.isNotEmpty()) {
                    LazyColumn(Modifier.weight(1f)) {
                        items(subs, key = { it.id }) { c -> ListCell(c.name, c.id == selectedId, accent) { onSelect(c.id) } }
                    }
                }
            }
        }
    }
}
