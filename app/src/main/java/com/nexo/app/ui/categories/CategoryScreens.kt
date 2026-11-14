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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexo.app.R
import com.nexo.app.core.model.Category
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.CategoryVisual
import com.nexo.app.core.model.CategoryVisuals
import com.nexo.app.core.model.RefData
import com.nexo.app.ui.designsystem.component.CategoryTile
import com.nexo.app.ui.designsystem.component.ChoiceDialog
import com.nexo.app.ui.designsystem.component.ConfirmDialog
import com.nexo.app.ui.designsystem.component.EmptyState
import com.nexo.app.ui.designsystem.component.NexoTopBar
import com.nexo.app.ui.designsystem.component.SectionHeader
import com.nexo.app.ui.designsystem.component.SettingRow
import com.nexo.app.ui.designsystem.component.SwitchRow
import com.nexo.app.ui.designsystem.component.rememberHaptics
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.designsystem.theme.CategoryHues
import com.nexo.app.ui.designsystem.theme.LocalNexoColors
import com.nexo.app.ui.designsystem.theme.NexoMotion
import com.nexo.app.ui.designsystem.theme.NexoShapes
import com.nexo.app.ui.designsystem.theme.NexoType
import com.nexo.app.ui.designsystem.theme.Space
import com.nexo.app.ui.navigation.CategoryFormKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SubcategoryListKey
import java.text.BreakIterator

/** Curated picks for the category form; anything else can be typed. */
private val EMOJI_CHOICES = listOf(
    "🍜", "🍱", "☕", "🛒", "🍺", "🥂", "🏠", "💡", "📱", "🌐",
    "🚌", "🚕", "🚗", "⛽", "✈️", "🏨", "🎬", "🎮", "🎵", "📚",
    "🎓", "🏥", "💊", "🧘", "🏋️", "💄", "👕", "👟", "🎁", "🐾",
    "🧸", "💼", "💵", "📈", "🐷", "🧾", "🛡️", "🔁", "🤲", "📦",
)

@Composable
private fun CategoryTileFor(visual: CategoryVisual?, size: Dp = 36.dp) {
    val colors = LocalNexoColors.current
    val v = visual ?: return
    CategoryTile(v.emoji, v.monogram, CategoryHues.of(v.colorIndex, colors.isDark), size = size)
}

@Composable
private fun EditableList(
    items: List<Category>,
    visual: (Category) -> CategoryVisual?,
    label: (Category) -> String,
    subtitle: (Category) -> String?,
    onClick: (Category) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (Category) -> Unit,
    header: @Composable () -> Unit = {},
    empty: String,
) {
    val colors = LocalNexoColors.current
    LazyColumn {
        item { header() }
        if (items.isEmpty()) item { EmptyState(empty) }
        itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
            Row(
                Modifier.animateItem().fillMaxWidth().heightIn(min = 64.dp).clickable {
                    onClick(c)
                }.padding(start = Space.gutter, end = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTileFor(visual(c))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f).padding(vertical = Space.m)) {
                    Text(
                        label(c),
                        style = NexoType.RowTitle,
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle(c)?.let {
                        Text(
                            it,
                            style = NexoType.Meta,
                            color = colors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = {
                    onReorder(items.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) })
                }, enabled = i > 0) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        stringResource(R.string.move_up),
                        tint = if (i > 0) {
                            colors.textSecondary
                        } else {
                            colors.hairline
                        },
                    )
                }
                IconButton(onClick = {
                    onReorder(items.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) })
                }, enabled = i < items.size - 1) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        stringResource(R.string.move_down),
                        tint = if (i < items.size - 1) {
                            colors.textSecondary
                        } else {
                            colors.hairline
                        },
                    )
                }
                IconButton(onClick = {
                    onDelete(c)
                }) {
                    Icon(
                        Icons.Rounded.RemoveCircleOutline,
                        stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding().height(Space.xl)) }
    }
}

/** Main category list. */
@Composable
fun CategoryListRoute(key: CategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    CategoryListScreen(
        kind = key.kind,
        state = state,
        onBack = nav::back,
        onAdd = { nav.navigate(CategoryFormKey(key.kind)) },
        onOpenSubcategories = { nav.navigate(SubcategoryListKey(it)) },
        onEditCategory = { nav.navigate(CategoryFormKey(key.kind, it)) },
        onReorder = vm::reorder,
        onDelete = vm::delete,
        onSetSubcategories = vm::setSubcategories,
    )
}

@Composable
internal fun CategoryListScreen(
    kind: CategoryKind,
    state: CategoryUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenSubcategories: (parentId: String) -> Unit,
    onEditCategory: (categoryId: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
    onDelete: (categoryId: String) -> Unit,
    onSetSubcategories: (Boolean) -> Unit,
) {
    val ref = state.ref
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val mains = ref.mainCategories(kind)
    val subsOn = ref.settings.subcategories
    Scaffold(topBar = {
        NexoTopBar(
            stringResource(
                if (kind == CategoryKind.INCOME) {
                    R.string.income_category
                } else {
                    R.string.expenses_category
                },
            ),
            onBack = onBack,
        ) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Rounded.Add, stringResource(R.string.action_add))
            }
        }
    }) { p ->
        Box(Modifier.padding(top = p.calculateTopPadding())) {
            EditableList(
                items = mains,
                visual = { ref.visual(it.id) },
                label = { c ->
                    val n = ref.subCategories(c.id).size
                    if (subsOn && n > 0) {
                        "${c.name} ($n)"
                    } else {
                        c.name
                    }
                },
                subtitle = { c ->
                    if (subsOn) {
                        ref.subCategories(c.id).joinToString(", ") {
                            it.name
                        }.ifEmpty { null }
                    } else {
                        null
                    }
                },
                onClick = { c ->
                    if (subsOn) {
                        onOpenSubcategories(c.id)
                    } else {
                        onEditCategory(c.id)
                    }
                },
                onReorder = onReorder,
                onDelete = { deleting = it.id },
                header = {
                    SwitchRow(stringResource(R.string.subcategory), subsOn, onSetSubcategories)
                },
                empty = stringResource(R.string.no_data),
            )
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            onDelete(id)
        }, onDismiss = {
            deleting = null
        })
    }
}

@Composable
fun SubcategoryListRoute(key: SubcategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    SubcategoryListScreen(
        parentId = key.parentId,
        state = state,
        onBack = nav::back,
        onEditParent = { nav.navigate(CategoryFormKey(it.kind, it.id)) },
        onAddSubcategory = { nav.navigate(CategoryFormKey(it.kind, parentId = it.id, sub = true)) },
        onEditSubcategory = { parent, subId ->
            nav.navigate(CategoryFormKey(parent.kind, subId, parent.id, sub = true))
        },
        onReorder = vm::reorder,
        onDelete = vm::delete,
    )
}

@Composable
internal fun SubcategoryListScreen(
    parentId: String,
    state: CategoryUiState,
    onBack: () -> Unit,
    onEditParent: (parent: Category) -> Unit,
    onAddSubcategory: (parent: Category) -> Unit,
    onEditSubcategory: (parent: Category, subId: String) -> Unit,
    onReorder: (ids: List<String>) -> Unit,
    onDelete: (categoryId: String) -> Unit,
) {
    val ref = state.ref
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val parent = ref.categoryById[parentId] ?: return
    Scaffold(topBar = {
        NexoTopBar(parent.name, onBack = onBack) {
            IconButton(onClick = {
                onEditParent(parent)
            }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = {
                onAddSubcategory(parent)
            }) { Icon(Icons.Rounded.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        Box(Modifier.padding(top = p.calculateTopPadding())) {
            EditableList(
                items = ref.subCategories(parent.id),
                visual = { ref.visual(it.id) },
                label = { it.name },
                subtitle = { null },
                onClick = { c ->
                    onEditSubcategory(parent, c.id)
                },
                onReorder = onReorder,
                onDelete = { deleting = it.id },
                empty = stringResource(R.string.no_subcategories),
            )
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = {
            onDelete(id)
        }, onDismiss = {
            deleting = null
        })
    }
}

/** Hue used least among the main categories of [kind], so new categories spread across the palette. */
private fun leastUsedHue(ref: RefData, kind: CategoryKind): Int {
    val used = ref.mainCategories(kind).mapNotNull {
        ref.visual(it.id)?.colorIndex
    }.groupingBy { it }.eachCount()
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
@Composable
fun CategoryFormRoute(key: CategoryFormKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.navigation) {
        when (val target = state.navigation) {
            CategoryNavigation.Back -> nav.back()

            is CategoryNavigation.SubcategoriesOf -> {
                nav.backTo { it is CategoryListKey }
                nav.navigate(SubcategoryListKey(target.parentId))
            }

            null -> return@LaunchedEffect
        }
        vm.onNavigationHandled()
    }
    CategoryFormScreen(
        key = key,
        state = state,
        onBack = nav::back,
        onSave = vm::save,
        onMoveUnder = vm::moveUnder,
        onMakeMain = vm::makeMain,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryFormScreen(
    key: CategoryFormKey,
    state: CategoryUiState,
    onBack: () -> Unit,
    onSave: (
        kind: CategoryKind,
        existingId: String?,
        name: String,
        parentId: String?,
        emoji: String?,
        colorIndex: Int?,
    ) -> Unit,
    onMoveUnder: (sourceId: String, targetId: String) -> Unit,
    onMakeMain: (categoryId: String) -> Unit,
) {
    val ref = state.ref
    val colors = LocalNexoColors.current
    val existing = key.categoryId?.let { ref.categoryById[it] }
    val isSub = key.sub || existing?.parentId != null
    var name by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.name ?: key.presetName ?: "")
    }
    var parent by rememberSaveable(existing?.id) {
        mutableStateOf(existing?.parentId ?: key.parentId)
    }
    var emoji by rememberSaveable(existing?.id) { mutableStateOf(existing?.emoji) }
    // New main categories start on the least-used hue; subcategories inherit unless one is picked.
    var color by rememberSaveable(existing?.id) {
        mutableStateOf(
            existing?.colorIndex
                ?: if (existing == null && !isSub) leastUsedHue(ref, key.kind) else null,
        )
    }
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
    val draft = (existing ?: Category("draft", "", key.kind, null, 0, createdAt = 0, updatedAt = 0))
        .copy(
            name = name,
            parentId = if (isSub) parent else null,
            emoji = emoji,
            colorIndex = color,
        )
    val preview = CategoryVisuals.resolve(draft, if (isSub) parentEntity else null)

    Scaffold(topBar = { NexoTopBar(stringResource(title), onBack = onBack) }) { p ->
        Column(
            Modifier.padding(
                top = p.calculateTopPadding(),
            ).fillMaxWidth().verticalScroll(
                rememberScrollState(),
            ).imePadding().navigationBarsPadding(),
        ) {
            val gutter = Modifier.padding(horizontal = Space.gutter)
            Row(
                gutter.fillMaxWidth().padding(vertical = Space.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryTile(
                    preview.emoji,
                    preview.monogram,
                    CategoryHues.of(preview.colorIndex, colors.isDark),
                    size = 64.dp,
                )
                Spacer(Modifier.width(Space.l))
                OutlinedTextField(
                    name,
                    {
                        name = it
                        error = null
                    },
                    label = {
                        Text(
                            stringResource(if (isSub) R.string.subcategory_name else R.string.name),
                        )
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let {
                        { Text(stringResource(it)) }
                    },
                    modifier = Modifier.weight(1f),
                    shape = NexoShapes.control,
                )
            }
            if (isSub) {
                SettingRow(
                    stringResource(R.string.category),
                    parentEntity?.name ?: stringResource(R.string.select_specific_category),
                    onClick = {
                        pickParent = true
                    },
                )
            }

            SectionHeader(stringResource(R.string.category_emoji))
            FlowRow(
                gutter.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                EmojiCell(
                    null,
                    selected = emoji == null,
                    label = stringResource(R.string.category_emoji_auto),
                ) {
                    emoji = null
                }
                val shown = if (emoji != null && emoji !in EMOJI_CHOICES) {
                    listOf(emoji!!) + EMOJI_CHOICES
                } else {
                    EMOJI_CHOICES
                }
                shown.forEach { e -> EmojiCell(e, selected = emoji == e) { emoji = e } }
            }
            OutlinedTextField(
                custom,
                { v ->
                    custom = v
                    firstGrapheme(v)?.let { emoji = it }
                },
                label = { Text(stringResource(R.string.category_emoji_custom)) },
                singleLine = true,
                shape = NexoShapes.control,
                modifier = gutter.fillMaxWidth().padding(top = Space.m),
            )
            if (emoji == null) {
                Text(
                    stringResource(R.string.category_emoji_auto_hint),
                    style = NexoType.Meta,
                    color = colors.textTertiary,
                    modifier = gutter.padding(top = Space.xs),
                )
            }

            SectionHeader(stringResource(R.string.category_color))
            FlowRow(
                gutter.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalArrangement = Arrangement.spacedBy(Space.s),
            ) {
                if (isSub) {
                    val inherited = parentEntity?.let { ref.visual(it.id)?.colorIndex } ?: 0
                    Swatch(
                        CategoryHues.of(inherited, colors.isDark),
                        selected = color == null,
                        description = stringResource(R.string.category_color_inherit),
                        inherit = true,
                    ) {
                        color = null
                    }
                }
                (0 until CategoryHues.count).forEach { i ->
                    Swatch(
                        CategoryHues.of(i, colors.isDark),
                        selected =
                            color == i || (color == null && !isSub && preview.colorIndex == i),
                        description = stringResource(R.string.category_color_n, i + 1),
                    ) { color = i }
                }
            }

            Button(onClick = {
                when {
                    name.isBlank() -> error = R.string.please_enter_contents

                    isSub && parent == null -> error = R.string.select_specific_category

                    else -> onSave(
                        key.kind,
                        existing?.id,
                        name,
                        if (isSub) parent else existing?.parentId,
                        emoji,
                        color,
                    )
                }
            }, modifier = gutter.fillMaxWidth().padding(top = Space.section).height(52.dp)) {
                Text(
                    stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (existing != null && isSub) {
                OutlinedButton(onClick = {
                    confirmMain = true
                }, modifier = gutter.fillMaxWidth().padding(top = Space.s).height(52.dp)) {
                    Text(stringResource(R.string.to_main_category))
                }
            }
            if (existing != null && !isSub && ref.settings.subcategories) {
                OutlinedButton(onClick = {
                    pickTarget = true
                }, modifier = gutter.fillMaxWidth().padding(top = Space.s).height(52.dp)) {
                    Text(stringResource(R.string.to_subcategory))
                }
            }
            Spacer(Modifier.height(Space.xl))
        }
    }
    if (pickParent) {
        ChoiceDialog(
            stringResource(R.string.category),
            ref.mainCategories(key.kind),
            ref.categoryById[parent],
            {
                it.name
            },
            onSelect = {
                parent = it.id
            },
            onDismiss = { pickParent = false },
        )
    }
    if (pickTarget && existing != null) {
        ChoiceDialog(
            stringResource(R.string.to_subcategory),
            ref.mainCategories(key.kind).filter {
                it.id !=
                    existing.id
            },
            null,
            { c ->
                val n = ref.subCategories(c.id).size
                "${c.name} ($n)"
            },
            onSelect = {
                confirmTarget = it.id
            },
            onDismiss = { pickTarget = false },
        )
    }
    confirmTarget?.let { target ->
        ConfirmDialog(
            stringResource(
                R.string.to_subcategory_confirm,
                existing?.name ?: "",
                ref.categoryName(target) ?: "",
            ),
            onConfirm = { onMoveUnder(existing!!.id, target) },
            onDismiss = { confirmTarget = null },
        )
    }
    if (confirmMain && existing != null) {
        ConfirmDialog(stringResource(R.string.to_main_confirm), onConfirm = {
            onMakeMain(existing.id)
        }, onDismiss = {
            confirmMain = false
        })
    }
}

@Composable
private fun EmojiCell(
    emoji: String?,
    selected: Boolean,
    label: String? = null,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val ring by animateColorAsState(
        if (selected) colors.primary else Color.Transparent,
        NexoMotion.standard(),
        label = "emojiRing",
    )
    val scale by animateFloatAsState(
        if (selected) 1f else 0.94f,
        NexoMotion.snappy(),
        label = "emojiScale",
    )
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(NexoShapes.tile)
            .background(if (selected) colors.raisedHigh else colors.raised)
            .border(1.5.dp, ring, NexoShapes.tile)
            .clickable(role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .semantics {
                this.selected = selected
                label?.let { contentDescription = it }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            Text(emoji, fontSize = 22.sp)
        } else {
            Text(label ?: "", style = NexoType.Caps, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun Swatch(
    color: Color,
    selected: Boolean,
    description: String,
    inherit: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalNexoColors.current
    val haptics = rememberHaptics()
    val scale by animateFloatAsState(
        if (selected) 1f else 0.86f,
        NexoMotion.snappy(),
        label = "swatch",
    )
    Box(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (inherit) colors.tint(color, colors.raised) else color)
            .border(
                if (inherit) 1.5.dp else 0.dp,
                if (inherit) color else Color.Transparent,
                CircleShape,
            )
            .clickable(role = Role.RadioButton) {
                haptics.tick()
                onClick()
            }
            .semantics {
                this.selected = selected
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                null,
                tint = if (inherit) color else colors.background,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val previewState = CategoryUiState(
    ref = PreviewData.ref.copy(settings = PreviewData.ref.settings.copy(subcategories = true)),
)

@Preview
@Composable
private fun CategoryListScreenPreview() = NexoPreview {
    CategoryListScreen(
        kind = CategoryKind.EXPENSE,
        state = previewState,
        onBack = {},
        onAdd = {},
        onOpenSubcategories = {},
        onEditCategory = {},
        onReorder = {},
        onDelete = {},
        onSetSubcategories = {},
    )
}

@Preview
@Composable
private fun SubcategoryListScreenPreview() = NexoPreview {
    SubcategoryListScreen(
        parentId = "food",
        state = previewState,
        onBack = {},
        onEditParent = {},
        onAddSubcategory = {},
        onEditSubcategory = { _, _ -> },
        onReorder = {},
        onDelete = {},
    )
}

@Preview
@Composable
private fun CategoryFormScreenPreview() = NexoPreview {
    CategoryFormScreen(
        key = CategoryFormKey(
            CategoryKind.EXPENSE,
            categoryId = "groceries",
            parentId = "food",
            sub = true,
        ),
        state = previewState,
        onBack = {},
        onSave = { _, _, _, _, _, _ -> },
        onMoveUnder = { _, _ -> },
        onMakeMain = {},
    )
}
