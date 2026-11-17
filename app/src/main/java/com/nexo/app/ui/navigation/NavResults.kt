package com.nexo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Handed back to the screens underneath when a screen closes.
@Serializable
sealed interface NavResult {
    // The Transactions tab moves to the day and scrolls it into view once.
    @Serializable
    data class Focus(private val day: String) : NavResult {
        constructor(date: LocalDate) : this(date.toString())

        val date: LocalDate get() = LocalDate.parse(day)
    }

    // Its row lights up when the list shows it.
    @Serializable
    data class TransactionSaved(val id: String) : NavResult

    // Its card lights up when the Notes view shows it.
    @Serializable
    data class MemoSaved(val id: String) : NavResult

    // A bookmark chosen for the entry form that opened the list.
    @Serializable
    data class FavoritePicked(val id: String) : NavResult
}

// Only the latest result of each kind is kept. Saved with the back stack, so results survive
// process death.
@Stable
class NavResultStore(initial: List<NavResult> = emptyList()) {
    private val items = mutableStateListOf<NavResult>().apply { addAll(initial) }

    val pending: List<NavResult> get() = items

    fun post(result: NavResult) {
        items.removeAll { it::class == result::class }
        items += result
    }

    fun consume(result: NavResult) {
        items.remove(result)
    }

    companion object {
        val Saver: Saver<NavResultStore, String> = Saver(
            save = { Json.encodeToString<List<NavResult>>(it.items.toList()) },
            restore = { NavResultStore(Json.decodeFromString<List<NavResult>>(it)) },
        )
    }
}

// Hands a pending result of [type] to [onResult] once, while the caller is composed.
@Composable
fun <T : NavResult> ReceiveNavResult(
    store: NavResultStore,
    type: KClass<T>,
    onResult: (T) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    val result = store.pending.lastOrNull(type::isInstance) as T?
    val current by rememberUpdatedState(onResult)
    LaunchedEffect(result) {
        if (result != null) {
            store.consume(result)
            current(result)
        }
    }
}
