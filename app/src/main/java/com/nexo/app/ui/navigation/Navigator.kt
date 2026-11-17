package com.nexo.app.ui.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

@Stable
class Navigator(
    private val stack: MutableList<NavKey>,
    val results: NavResultStore = NavResultStore(),
) {
    fun navigate(key: NavKey) {
        stack.add(key)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    // Pops until [predicate] matches the top, or only the root remains.
    fun backTo(predicate: (NavKey) -> Boolean) {
        while (stack.size > 1 && !predicate(stack.last())) stack.removeAt(stack.lastIndex)
    }

    fun replace(key: NavKey) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(key)
    }

    fun resetTo(vararg keys: NavKey) {
        stack.clear()
        stack.addAll(keys)
    }

    val top: NavKey? get() = stack.lastOrNull()
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
