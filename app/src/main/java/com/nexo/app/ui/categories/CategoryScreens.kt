package com.nexo.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nexo.app.R
import com.nexo.app.core.database.entity.CategoryEntity
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.data.CategoryRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.RefData
import com.nexo.app.ui.components.ChoiceDialog
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.components.SettingRow
import com.nexo.app.ui.components.SwitchRow
import com.nexo.app.ui.navigation.CategoryFormKey
import com.nexo.app.ui.navigation.CategoryListKey
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.navigation.SubcategoryListKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoryViewModel @Inject constructor(private val refs: RefRepository, val repo: CategoryRepository) : ViewModel() {
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun launch(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun setSubcategories(v: Boolean) = viewModelScope.launch { refs.updateSettings { it.copy(subcategories = v) } }
}

@Composable
private fun EditableList(
    items: List<CategoryEntity>,
    label: (CategoryEntity) -> String,
    subtitle: (CategoryEntity) -> String?,
    onClick: (CategoryEntity) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    header: @Composable () -> Unit = {},
    empty: String,
) {
    LazyColumn {
        item { header() }
        if (items.isEmpty()) item { EmptyState(empty) }
        itemsIndexed(items, key = { _, c -> c.id }) { i, c ->
            Row(Modifier.fillMaxWidth().clickable { onClick(c) }.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(label(c))
                    subtitle(c)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
                IconButton(onClick = { onReorder(items.map { it.id }.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up)) }
                IconButton(onClick = { onReorder(items.map { it.id }.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < items.size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down)) }
                IconButton(onClick = { onDelete(c) }) { Icon(Icons.Filled.RemoveCircle, stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** Main category list (ST-061). */
@Composable
fun CategoryListScreen(key: CategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val mains = ref.mainCategories(key.kind)
    val subsOn = ref.settings.subcategories
    Scaffold(topBar = {
        NexoTopBar(stringResource(if (key.kind == CategoryKind.INCOME) R.string.income_category else R.string.expenses_category), onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(CategoryFormKey(key.kind)) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            EditableList(
                items = mains,
                label = { c -> val n = ref.subCategories(c.id).size; if (subsOn && n > 0) "${c.name} ($n)" else c.name },
                subtitle = { c -> if (subsOn) ref.subCategories(c.id).joinToString(", ") { it.name }.ifEmpty { null } else null },
                onClick = { c -> if (subsOn) nav.navigate(SubcategoryListKey(c.id)) else nav.navigate(CategoryFormKey(key.kind, c.id)) },
                onReorder = { ids -> vm.launch { vm.repo.reorder(ids) } },
                onDelete = { deleting = it.id },
                header = {
                    SwitchRow(stringResource(R.string.subcategory), subsOn, vm::setSubcategories)
                    HorizontalDivider()
                },
                empty = stringResource(R.string.no_data),
            )
        }
    }
    deleting?.let { id -> ConfirmDialog(stringResource(R.string.confirm_delete), onConfirm = { vm.launch { vm.repo.delete(id) } }, onDismiss = { deleting = null }) }
}

/** Subcategory list (ST-064). */
@Composable
fun SubcategoryListScreen(key: SubcategoryListKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val parent = ref.categoryById[key.parentId] ?: return
    Scaffold(topBar = {
        NexoTopBar(parent.name, onBack = { nav.back() }) {
            IconButton(onClick = { nav.navigate(CategoryFormKey(parent.kind, parent.id)) }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            IconButton(onClick = { nav.navigate(CategoryFormKey(parent.kind, parentId = parent.id, sub = true)) }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        }
    }) { p ->
        androidx.compose.foundation.layout.Box(Modifier.padding(p)) {
            EditableList(
                items = ref.subCategories(parent.id),
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

/** Add / edit main or sub category, with the main ↔ sub conversions (ST-062, ST-065…ST-067). */
@Composable
fun CategoryFormScreen(key: CategoryFormKey, vm: CategoryViewModel = hiltViewModel()) {
    val nav = LocalNavigator.current
    val ref by vm.ref.collectAsStateWithLifecycle()
    val existing = key.categoryId?.let { ref.categoryById[it] }
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: key.presetName ?: "") }
    var parent by rememberSaveable(existing?.id) { mutableStateOf(existing?.parentId ?: key.parentId) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickParent by rememberSaveable { mutableStateOf(false) }
    var pickTarget by rememberSaveable { mutableStateOf(false) }
    var confirmTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmMain by rememberSaveable { mutableStateOf(false) }
    val isSub = key.sub || existing?.parentId != null
    val title = when {
        isSub && existing == null -> R.string.subcategory
        isSub -> R.string.modify_subcategory
        existing != null -> R.string.change_category
        key.kind == CategoryKind.INCOME -> R.string.income_category
        else -> R.string.expenses_category
    }
    Scaffold(topBar = { NexoTopBar(stringResource(title), onBack = { nav.back() }) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            if (isSub) {
                SettingRow(stringResource(R.string.category), parent?.let { ref.categoryName(it) } ?: stringResource(R.string.select_specific_category), onClick = { pickParent = true })
            }
            OutlinedTextField(
                name, { name = it; error = null }, label = { Text(stringResource(if (isSub) R.string.subcategory_name else R.string.name)) },
                singleLine = true, isError = error != null, supportingText = error?.let { { Text(stringResource(it)) } }, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                when {
                    name.isBlank() -> error = R.string.please_enter_contents
                    isSub && parent == null -> error = R.string.select_specific_category
                    else -> vm.launch {
                        if (existing == null) vm.repo.add(key.kind, name, if (isSub) parent else null)
                        else vm.repo.update(existing.id, name, if (isSub) parent else existing.parentId)
                        nav.back()
                    }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(stringResource(R.string.action_save)) }
            if (existing != null && isSub) {
                OutlinedButton(onClick = { confirmMain = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.to_main_category)) }
            }
            if (existing != null && !isSub && ref.settings.subcategories) {
                OutlinedButton(onClick = { pickTarget = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.to_subcategory)) }
            }
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
