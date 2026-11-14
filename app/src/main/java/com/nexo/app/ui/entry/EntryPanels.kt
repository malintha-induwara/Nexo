package com.nexo.app.ui.entry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexo.app.R
import com.nexo.app.core.model.Account
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.Category
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.GroupStatus
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.NavMotion
import com.nexo.app.ui.designsystem.component.RoundIconButton
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import java.util.Locale

private val PanelHeight = 320.dp

@Composable
private fun PanelFrame(content: @Composable () -> Unit) {
    val colors = LocalNexoColors.current
    Column(Modifier.fillMaxWidth().clip(NexoShapes.sheet).background(colors.surface).navigationBarsPadding().height(PanelHeight)) { content() }
}

@Composable
private fun PanelHeader(title: String, onClose: () -> Unit, onTitleClick: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val colors = LocalNexoColors.current
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = Space.s, end = Space.s), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(CircleShape).then(if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier).padding(horizontal = Space.s, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onTitleClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = colors.text, modifier = Modifier.size(20.dp))
            Text(title.uppercase(Locale.getDefault()), style = NexoType.Caps, color = if (onTitleClick != null) colors.text else colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        extra()
        RoundIconButton(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.action_close), onClose, size = 36.dp, tint = colors.textSecondary, container = Color.Transparent)
    }
}

/** A name in a rounded cell; selection fills it with the entry's colour at low strength and rings it. */
@Composable
private fun NameCell(text: String, selected: Boolean, accent: Color, dimmed: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (selected) colors.tint(accent, colors.raised) else colors.raised, NexoMotion.standard(), label = "cellBg")
    val ring by animateColorAsState(if (selected) accent else Color.Transparent, NexoMotion.standard(), label = "cellRing")
    Box(
        Modifier
            .padding(Space.xs)
            .fillMaxWidth()
            .height(48.dp)
            .clip(NexoShapes.control)
            .background(bg)
            .border(1.5.dp, ring, NexoShapes.control)
            .clickable(role = Role.RadioButton) { haptics.tick(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Space.s),
            color = if (dimmed) colors.textTertiary else colors.text,
        )
    }
}

@Composable
private fun ListCell(text: String, selected: Boolean, accent: Color, chevron: Boolean = false, dimmed: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (selected) colors.tint(accent, colors.surface) else Color.Transparent, NexoMotion.standard(), label = "listCell")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.xs).clip(NexoShapes.control).background(bg)
            .clickable { haptics.tick(); onClick() }.padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (dimmed) colors.textTertiary else colors.text)
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}

/** Account selection panel: flat grid or grouped two-pane. */
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
    val colors = LocalNexoColors.current
    PanelFrame {
        PanelHeader(title, onClose) {
            RoundIconButton(if (grouped) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda, stringResource(R.string.toggle_grouping), onToggleGrouped, size = 36.dp, tint = colors.textSecondary, container = Color.Transparent)
            RoundIconButton(Icons.Outlined.Edit, stringResource(R.string.action_edit), onEdit, size = 36.dp, tint = colors.textSecondary, container = Color.Transparent)
        }
        val visible = ref.orderedAccounts().filter { a ->
            val g = ref.groupById[a.groupId]
            g != null && g.status == GroupStatus.ACTIVE && (a.status == AccountStatus.ACTIVE || (grouped && a.status == AccountStatus.HIDDEN))
        }
        if (!grouped) {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(horizontal = Space.s), contentPadding = PaddingValues(bottom = Space.s)) {
                items(visible.filter { it.status == AccountStatus.ACTIVE }, key = { it.id }) { a ->
                    NameCell(a.name, a.id == selectedId, accent) { onSelect(a.id) }
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
                    val inGroup: List<Account> = visible.filter { it.groupId == group }.sortedBy { if (it.status == AccountStatus.HIDDEN) 1 else 0 }
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
    val colors = LocalNexoColors.current
    Box(
        Modifier.padding(Space.xs).fillMaxWidth().height(48.dp).clip(NexoShapes.control)
            .border(1.dp, colors.hairline, NexoShapes.control).clickable(onClick = onAdd),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, stringResource(R.string.add_account), tint = colors.textSecondary) }
}

/** An emoji tile with its name beneath, ringed in the entry's colour when chosen. */
@Composable
private fun CategoryCell(category: Category, ref: RefData, selected: Boolean, accent: Color, hasChildren: Boolean, label: String = category.name, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val v = ref.visual(category.id)
    val ring by animateColorAsState(if (selected) accent else Color.Transparent, NexoMotion.standard(), label = "catRing")
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, NexoMotion.snappy(), label = "catScale")
    Column(
        Modifier
            .padding(horizontal = Space.xxs, vertical = Space.xs)
            .clip(NexoShapes.control)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { haptics.tick(); onClick() }
            .padding(vertical = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }.border(2.dp, ring, NexoShapes.tile).padding(3.dp)) {
            if (v != null) CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark), size = 44.dp, over = colors.surface)
            if (hasChildren) {
                Box(Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(colors.raisedHigh), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            label, style = MaterialTheme.typography.labelMedium, color = if (selected) colors.text else colors.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().heightIn(min = 16.dp),
        )
    }
}

/**
 * Category selection panel. A grid of main categories; with subcategories on, a main
 * category that has subcategories opens its own grid, where the first cell picks the main
 * category itself (same rule as the old two-pane picker: first tap opens, second tap selects).
 */
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
    val colors = LocalNexoColors.current
    val subsOn = ref.settings.subcategories
    val selectedMain = ref.mainCategoryId(selectedId)
    var open by remember(selectedId) { mutableStateOf(if (subsOn) selectedMain?.takeIf { ref.subCategories(it).isNotEmpty() } else null) }
    val openCategory = open?.let { ref.categoryById[it] }
    PanelFrame {
        PanelHeader(openCategory?.name ?: stringResource(R.string.category), onClose, onTitleClick = if (openCategory != null) ({ open = null }) else null) {
            RoundIconButton(Icons.Outlined.Edit, stringResource(R.string.action_edit), onEdit, size = 36.dp, tint = colors.textSecondary, container = Color.Transparent)
        }
        AnimatedContent(
            targetState = open,
            transitionSpec = { NavMotion.lateral(this, if (targetState != null) 1 else -1) },
            modifier = Modifier.fillMaxSize(),
            label = "categoryLevel",
        ) { o ->
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize().padding(horizontal = Space.s), contentPadding = PaddingValues(bottom = Space.s)) {
                if (o == null) {
                    items(ref.mainCategories(kind), key = { it.id }) { c ->
                        val subs = if (subsOn) ref.subCategories(c.id) else emptyList()
                        CategoryCell(c, ref, c.id == selectedMain, accent, hasChildren = subs.isNotEmpty()) {
                            if (subs.isEmpty()) onSelect(c.id) else open = c.id
                        }
                    }
                } else {
                    val main = ref.categoryById[o]
                    if (main != null) item(key = "main_$o") { CategoryCell(main, ref, selectedId == o, accent, hasChildren = false) { onSelect(o) } }
                    items(ref.subCategories(o), key = { it.id }) { c -> CategoryCell(c, ref, c.id == selectedId, accent, hasChildren = false) { onSelect(c.id) } }
                }
            }
        }
    }
}
