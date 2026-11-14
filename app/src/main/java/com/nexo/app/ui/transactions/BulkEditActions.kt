package com.nexo.app.ui.transactions

import com.nexo.app.data.repository.TransactionsRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the bulk-edit dialogs can do to the selected rows. */
class BulkEditActions(
    val delete: () -> Unit,
    val setDate: (LocalDate) -> Unit,
    val setNote: (String) -> Unit,
    val setCategory: (String) -> Unit,
    val setAccount: (String) -> Unit,
    val setTransfer: (from: String, to: String) -> Unit,
)

/** Bulk edits of [selection] run in a ViewModel's [scope]; the selection clears once each edit is written. */
fun bulkEditActions(
    transactions: TransactionsRepository,
    scope: CoroutineScope,
    selection: MutableStateFlow<Set<String>>,
): BulkEditActions {
    fun run(edit: suspend (Set<String>) -> Unit) {
        val ids = selection.value
        if (ids.isEmpty()) return
        scope.launch {
            edit(ids)
            selection.value = emptySet()
        }
    }
    return BulkEditActions(
        delete = { run { transactions.delete(it) } },
        setDate = { date -> run { transactions.setDate(it, date) } },
        setNote = { note -> run { transactions.setNote(it, note) } },
        setCategory = { id -> run { transactions.setCategory(it, id) } },
        setAccount = { id -> run { transactions.setAccount(it, id) } },
        setTransfer = { from, to -> run { transactions.setTransfer(it, from, to) } },
    )
}
