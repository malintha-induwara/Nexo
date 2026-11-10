package com.nexo.app.ui.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.nexo.app.core.database.entity.FavoriteEntity
import com.nexo.app.core.model.AccountStatus
import com.nexo.app.core.model.TxKind
import com.nexo.app.core.money.AmountFormatter
import com.nexo.app.data.FavoriteRepository
import com.nexo.app.data.RefRepository
import com.nexo.app.domain.RefData
import com.nexo.app.ui.HomeState
import com.nexo.app.ui.components.ConfirmDialog
import com.nexo.app.ui.components.EmptyState
import com.nexo.app.ui.components.NexoTopBar
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.EntryMode
import com.nexo.app.ui.navigation.LocalNavigator
import com.nexo.app.ui.theme.LocalMoneyColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    val repo: FavoriteRepository,
    refs: RefRepository,
    val home: HomeState,
) : ViewModel() {
    val favorites = repo.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ref = refs.ref.stateIn(viewModelScope, SharingStarted.Eagerly, RefData())
    fun move(ids: List<String>) = viewModelScope.launch { repo.reorder(ids) }
    fun delete(ids: List<String>) = viewModelScope.launch { repo.delete(ids) }
}

@Composable
fun accountLabel(ref: RefData, id: String?): String {
    val a = id?.let { ref.accountById[it] } ?: return stringResource(R.string.none)
    return if (a.status == AccountStatus.DELETED || a.status == AccountStatus.PURGED) "##${a.name}##" else a.name
}

/** Bookmarks (TX-031). */
@Composable
fun BookmarksScreen(key: BookmarksKey, vm: BookmarksViewModel = hiltViewModel()) {
    val list by vm.favorites.collectAsStateWithLifecycle()
    val ref by vm.ref.collectAsStateWithLifecycle()
    val nav = LocalNavigator.current
    val money = LocalMoneyColors.current
    var editing by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var confirm by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        NexoTopBar(stringResource(R.string.bookmarks), onBack = { nav.back() }) {
            if (editing) {
                IconButton(onClick = { if (checked.isNotEmpty()) confirm = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                TextButton(onClick = { editing = false; checked = emptySet() }) { Text(stringResource(R.string.action_done)) }
            } else if (list.isNotEmpty()) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.action_edit)) }
            }
        }
    }) { padding ->
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringResource(R.string.bookmark_help_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.bookmark_help), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                EmptyState()
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(list, key = { _, f -> f.id }) { index, f ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        when {
                            editing -> checked = if (f.id in checked) checked - f.id else checked + f.id
                            key.pickForForm -> { vm.home.pickFavorite(f.id); nav.back() }
                            else -> nav.replace(EntryKey(mode = EntryMode.FAVORITE, favoriteId = f.id))
                        }
                    }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing) Checkbox(f.id in checked, onCheckedChange = null, modifier = Modifier.padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(favoriteTitle(ref, f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(favoriteSubtitle(ref, f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        if (ref.settings.showDescription && f.description.isNotBlank()) Text(f.description.lines().first(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                    if (editing) {
                        IconButton(onClick = { vm.move(list.map { it.id }.toMutableList().apply { add(index - 1, removeAt(index)) }) }, enabled = index > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.move_up)) }
                        IconButton(onClick = { vm.move(list.map { it.id }.toMutableList().apply { add(index + 1, removeAt(index)) }) }, enabled = index < list.size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.move_down)) }
                    } else {
                        Text(
                            AmountFormatter.format(f.amount, ref.currency(f.currencyId)),
                            color = when (f.kind) {
                                TxKind.INCOME -> money.income
                                TxKind.EXPENSE -> money.expense
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (confirm) ConfirmDialog(stringResource(R.string.want_to_delete_n, checked.size), onConfirm = { vm.delete(checked.toList()); checked = emptySet() }, onDismiss = { confirm = false })
}

@Composable
private fun favoriteTitle(ref: RefData, f: FavoriteEntity): String = f.note.ifBlank {
    if (f.kind.isTransfer) "${accountLabel(ref, f.accountId)} → ${accountLabel(ref, f.toAccountId)}" else accountLabel(ref, f.accountId)
}

@Composable
private fun favoriteSubtitle(ref: RefData, f: FavoriteEntity): String {
    if (f.kind.isTransfer) {
        val pair = "${accountLabel(ref, f.accountId)} → ${accountLabel(ref, f.toAccountId)}"
        return stringResource(R.string.transfer) + if (f.note.isNotBlank()) " · $pair" else ""
    }
    val main = ref.categoryName(ref.mainCategoryId(f.categoryId)) ?: stringResource(R.string.none)
    val sub = ref.subCategoryId(f.categoryId)?.let { ref.categoryName(it) }
    val cat = if (sub != null) "$main/$sub" else main
    return if (f.note.isNotBlank()) "$cat · ${accountLabel(ref, f.accountId)}" else cat
}
