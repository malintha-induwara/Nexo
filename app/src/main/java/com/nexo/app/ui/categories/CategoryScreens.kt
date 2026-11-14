package com.nexo.app.ui.categories

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.data.CategoryRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.CategoryVisual
import com.nexo.app.domain.CategoryVisuals
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.CategoryTile
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SectionHeader
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.components.SwitchRow
import com.nexo.app.ui.components.rememberHaptics
import com.nexo.app.ui.navigation.CategoryFormKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SubcategoryListKey
import com.nexo.app.ui.theme.CategoryHues
import com.nexo.app.ui.theme.LocalNexoColors
import com.nexo.app.ui.theme.NexoMotion
import com.nexo.app.ui.theme.NexoShapes
import com.nexo.app.ui.theme.NexoType
import com.nexo.app.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.BreakIterator
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(private val refs: RefRepository, val repo: CategoryRepository) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun setSubcategories(v: Boolean) = viewModelScope.launch { refs.updateSettings { it.copy(subcategories = v) } }
}

/** Curated picks for the category form; anything else can be typed. */
private val EMOJI_CHOICES = listOf(
    "🍜", "🍱", "☕", "🛒", "🍺", "🥂", "🏠", "💡", "📱", "🌐",
    "🚌", "🚕", "🚗", "⛽", "✈️", "🏨", "🎬", "🎮", "🎵", "📚",
    "🎓", "🏥", "💊", "🧘", "🏋️", "💄", "👕", "👟", "🎁", "🐾",
    "🧸", "💼", "💵", "📈", "🐷", "🧾", "🛡️", "🔁", "🤲", "📦",
)

@Composable
private fun CategoryTileFor(visual: CategoryVisual?, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val colors = LocalNexoColors.current
    val v = visual ?: return
    CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark), size = size)
}

@Composable
private fun EditableList(
    items: List<CategoryEntity>,
    visual: (CategoryEntity) -> CategoryVisual?,
    label: (CategoryEntity) -> String,
    subtitle: (CategoryEntity) -> String?,
    onClick: (CategoryEntity) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    header: @Composable () -> Unit = {},
    empty: String,
) {
    val colors = LocalNexoColors.current
    LazyColumn {
        item { header() }
        if (items.isEmpty()) item { EmptyState(empty) }
        itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
            Row(
                Modifier.animateItem().fillMaxWidth().heightIn(min = 64.dp).clickable { onClick(c) }.padding(start = Space.gutter, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTileFor(visual(c))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f).padding(vertical = Space.m)) {
                    Text(label(c), style = NexoType.RowTitle, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle(c)?.let { Text(it, style = NexoType.Meta, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                IconButton(onClick = { onReorder(items.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                    Icon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.move_up), tint = if (i > 0) colors.textSecondary else colors.hairline)
                }
                IconButton(onClick = { onReorder(items.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < items.size - 1) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.move_down), tint = if (i < items.size - 1) colors.textSecondary else colors.hairline)
                }
                IconButton(onClick = { onDelete(c) }) { Icon(Icons.Rounded.RemoveCircleOutline, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(Space.xl)) }
    }
}

/** Main category list. */
@Composable
fun CategoryListScreen(key: CategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val mains = ref.mainCategories(key.kind)
    val subsOn = ref.settings.subcategories
    Scaffold(topBar = {
        NexoTopBar(stringResource(if (key.kind == CategoryKind.INCOME) R.string.income_category else R.string.expenses_category), onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(CategoryFormKey(key.kind)) }) { Icon(Icons.Rounded.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        Box(Modifier.padding(top = p.calculateTopPadding())) {
            EditableList(
                items = mains,
                visual = { ref.visual(it.id) },
                label = { c -> val n = ref.subCategories(c.id).size; if (subsOn && n > 0) "${c.name} ($n)" else c.name },
                subtitle = { c -> if (subsOn) ref.subCategories(c.id).joinToString(", ") { it.name }.ifEmpty { null } else null },
                onClick = { c -> if (subsOn) nav.navigate(SubcategoryListKey(c.id)) else nav.navigate(CategoryFormKey(key.kind, c.id)) },
                onReorder = { ids -> vm.launch { vm.repo.reorder(ids) } },
                onDelete = { deleting = it.id },
                header = { SwitchRow(stringResource(R.string.subcategory), subsOn, vm::setSubcategories) },
                empty = stringResource(R.string.no_data),
            )
        }
    }
    deleting?.let { id -> ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.launch { vm.repo.delete(id) } }, onDismiss = { deleting = null }) }
}

@Composable
fun SubcategoryListScreen(key: SubcategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val parent = ref.categoryById[key.parentId] ?: return
    Scaffold(topBar = {
        NexoTopBar(parent.name, onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(CategoryFormKey(parent.kind, parent.id)) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = { nav.navigate(CategoryFormKey(parent.kind, parentId = parent.id, sub = true)) }) { Icon(Icons.Rounded.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        Box(Modifier.padding(top = p.calculateTopPadding())) {
            EditableList(
                items = ref.subCategories(parent.id),
                visual = { ref.visual(it.id) },
                label = { it.name }, subtitle = { null },
                onClick = { c -> nav.navigate(CategoryFormKey(parent.kind, c.id, parent.id, sub = true)) },
                onReorder = { ids -> vm.launch { vm.repo.reorder(ids) } },
                onDelete = { deleting = it.id },
                empty = stringResource(R.string.no_subcategories),
            )
        }
    }
    deleting?.let { id -> ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.launch { vm.repo.delete(id) } }, onDismiss = { deleting = null }) }
}

/** Hue used least among the main categories of [kind], so new categories spread across the palette. */
private fun leastUsedHue(ref: RefData, kind: CategoryKind): Int {
    val used = ref.mainCategories(kind).mapNotNull { ref.visual(it.id)?.colorIndex }.groupingBy { it }.eachCount()
    return (0 until CategoryHues.count).minBy { used[it] ?: 0 }
}

/** First user-perceived character, so a typed flag or family emoji stays whole. */
private fun firstGrapheme(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val it = BreakIterator.getCharacterInstance().apply { setText(t) }
    val end = it.next()
    return if (end == BreakIterator.DONE) t else t.substring(0, end)
}

/** Add / edit main or sub category, with the main ↔ sub conversions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFormScreen(key: CategoryFormKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val colors = LocalNexoColors.current
    val existing = key.categoryId?.let { ref.categoryById[it] }
    val isSub = key.sub || existing?.parentId != null
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: key.presetName ?: "") }
    var parent by rememberSaveable(existing?.id) { mutableStateOf(existing?.parentId ?: key.parentId) }
    var emoji by rememberSaveable(existing?.id) { mutableStateOf(existing?.emoji) }
    // New main categories start on the least-used hue; subcategories inherit unless one is picked.
    var color by rememberSaveable(existing?.id) { mutableStateOf(existing?.colorIndex ?: if (existing == null && !isSub) leastUsedHue(ref, key.kind) else null) }
    var custom by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickParent by rememberSaveable { mutableStateOf(false) }
    var pickTarget by rememberSaveable { mutableStateOf(false) }
    var confirmTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmMain by rememberSaveable { mutableStateOf(false) }
    val title = when {
        isSub && existing == null -> R.string.subcategory
        isSub -> R.string.modify_subcategory
        existing != null -> R.string.change_category
        key.kind == CategoryKind.INCOME -> R.string.income_category
        else -> R.string.expenses_category
    }
    val parentEntity = parent?.let { ref.categoryById[it] }
    val draft = (existing ?: CategoryEntity("draft", "", key.kind, null, 0, createdAt = 0, updatedAt = 0))
        .copy(name = name, parentId = if (isSub) parent else null, emoji = emoji, colorIndex = color)
    val preview = CategoryVisuals.resolve(draft, if (isSub) parentEntity else null)

    Scaffold(topBar = { NexoTopBar(stringResource(title), onBack = { nav.back() }) }) { p ->
        Column(
            Modifier.padding(top = p.calculateTopPadding()).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding(),
        ) {
            val gutter = Modifier.padding(horizontal = Space.gutter)
            Row(gutter.fillMaxWidth().padding(vertical = Space.l), verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(preview.emoji, preview.monogram, CategoryHues.of(preview.colorIndex, colors.isDark), size = 64.dp)
                Spacer(Modifier.width(Space.l))
                OutlinedTextField(
                    name, { name = it; error = null }, label = { Text(stringResource(if (isSub) R.string.subcategory_name else R.string.name)) },
                    singleLine = true, isError = error != null, supportingText = error?.let { { Text(stringResource(it)) } }, modifier = Modifier.weight(1f),
                    shape = NexoShapes.control,
                )
            }
            if (isSub) {
                SettingRow(stringResource(R.string.category), parentEntity?.name ?: stringResource(R.string.select_specific_category), onClick = { pickParent = true })
            }

            SectionHeader(stringResource(R.string.category_emoji))
            FlowRow(gutter.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                EmojiCell(null, selected = emoji == null, label = stringResource(R.string.category_emoji_auto)) { emoji = null }
                val shown = if (emoji != null && emoji !in EMOJI_CHOICES) listOf(emoji!!) + EMOJI_CHOICES else EMOJI_CHOICES
                shown.forEach { e -> EmojiCell(e, selected = emoji == e) { emoji = e } }
            }
            OutlinedTextField(
                custom,
                { v -> custom = v; firstGrapheme(v)?.let { emoji = it } },
                label = { Text(stringResource(R.string.category_emoji_custom)) },
                singleLine = true,
                shape = NexoShapes.control,
                modifier = gutter.fillMaxWidth().padding(top = Space.m),
            )
            if (emoji == null) {
                Text(stringResource(R.string.category_emoji_auto_hint), style = NexoType.Meta, color = colors.textTertiary, modifier = gutter.padding(top = Space.xs))
            }

            SectionHeader(stringResource(R.string.category_color))
            FlowRow(gutter.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (isSub) {
                    val inherited = parentEntity?.let { ref.visual(it.id)?.colorIndex } ?: 0
                    Swatch(CategoryHues.of(inherited, colors.isDark), selected = color == null, description = stringResource(R.string.category_color_inherit), inherit = true) { color = null }
                }
                (0 until CategoryHues.count).forEach { i ->
                    Swatch(CategoryHues.of(i, colors.isDark), selected = color == i || (color == null && !isSub && preview.colorIndex == i), description = stringResource(R.string.category_color_n, i + 1)) { color = i }
                }
            }

            Button(onClick = {
                when {
                    name.isBlank() -> error = R.string.please_enter_contents
                    isSub && parent == null -> error = R.string.select_specific_category
                    else -> vm.launch {
                        if (existing == null) vm.repo.add(key.kind, name, if (isSub) parent else null, emoji, color)
                        else vm.repo.update(existing.id, name, if (isSub) parent else existing.parentId, emoji, color)
                        nav.back()
                    }
                }
            }, modifier = gutter.fillMaxWidth().padding(top = Space.section).height(52.dp)) { Text(stringResource(R.string.action_save), style = MaterialTheme.typography.labelLarge) }
            if (existing != null && isSub) {
                OutlinedButton(onClick = { confirmMain = true }, modifier = gutter.fillMaxWidth().padding(top = Space.s).height(52.dp)) { Text(stringResource(R.string.to_main_category)) }
            }
            if (existing != null && !isSub && ref.settings.subcategories) {
                OutlinedButton(onClick = { pickTarget = true }, modifier = gutter.fillMaxWidth().padding(top = Space.s).height(52.dp)) { Text(stringResource(R.string.to_subcategory)) }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
    if (pickParent) {
        ChoiceDialog(stringResource(R.string.category), ref.mainCategories(key.kind), ref.categoryById[parent], { it.name }, onSelect = { parent = it.id }, onDismiss = { pickParent = false })
    }
    if (pickTarget && existing != null) {
        ChoiceDialog(
            stringResource(R.string.to_subcategory), ref.mainCategories(key.kind).filter { it.id != existing.id }, null,
            { c -> val n = ref.subCategories(c.id).size; "${c.name} ($n)" }, onSelect = { confirmTarget = it.id }, onDismiss = { pickTarget = false },
        )
    }
    confirmTarget?.let { target ->
        ConfirmDialog(
            stringResource(R.string.to_subcategory_confirm, existing?.name ?: "", ref.categoryName(target) ?: ""),
            onConfirm = { vm.launch { vm.repo.toSub(existing!!.id, target); nav.backTo { it is CategoryListKey }; nav.navigate(SubcategoryListKey(target)) } },
            onDismiss = { confirmTarget = null },
        )
    }
    if (confirmMain && existing != null) {
        ConfirmDialog(stringResource(R.string.to_main_confirm), onConfirm = { vm.launch { vm.repo.toMain(existing.id); nav.back() } }, onDismiss = { confirmMain = false })
    }
}

@Composable
private fun EmojiCell(emoji: String?, selected: Boolean, label: String? = null, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val ring by animateColorAsState(if (selected) colors.primary else Color.Transparent, NexoMotion.standard(), label = "emojiRing")
    val scale by animateFloatAsState(if (selected) 1f else 0.94f, NexoMotion.snappy(), label = "emojiScale")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(NexoShapes.tile)
            .background(if (selected) colors.raisedHigh else colors.raised)
            .border(1.5.dp, ring, NexoShapes.tile)
            .clickable(role = Role.RadioButton) { haptics.tick(); onClick() }
            .semantics { this.selected = selected; label?.let { contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) Text(emoji, fontSize = 22.sp)
        else Text(label ?: "", style = NexoType.Caps, color = colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, description: String, inherit: Boolean = false, onClick: () -> Unit) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(if (selected) 1f else 0.86f, NexoMotion.snappy(), label = "swatch")
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (inherit) colors.tint(color, colors.raised) else color)
            .border(if (inherit) 1.5.dp else 0.dp, if (inherit) color else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton) { haptics.tick(); onClick() }
            .semantics { this.selected = selected; contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (inherit) color else colors.background, modifier = Modifier.size(20.dp))
    }
}
