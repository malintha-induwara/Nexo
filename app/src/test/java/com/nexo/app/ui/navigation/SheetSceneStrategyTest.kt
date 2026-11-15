package com.nexo.app.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SheetSceneStrategyTest {
    @get:Rule
    val rule = createComposeRule()

    private data object Main : NavKey

    private data object Drawer : NavKey

    private data object Other : NavKey

    @Test
    fun aDrawerReturnedToAfterAWhileStillFollowsItsState() {
        val stack = mutableStateListOf<NavKey>(Main)
        val count = MutableStateFlow(0)
        var drawerLifecycle: Lifecycle? = null
        rule.setContent {
            val host = LocalLifecycleOwner.current
            val sheets = remember { SheetSceneStrategy(host) { it in stack } }
            NavDisplay(
                backStack = stack,
                onBack = { stack.removeAt(stack.lastIndex) },
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
                sceneStrategies = listOf(sheets),
                entryProvider = entryProvider {
                    entry<Main> { Text("main") }
                    entry<Drawer>(metadata = SheetSceneStrategy.sheet(Drawer)) {
                        drawerLifecycle = LocalLifecycleOwner.current.lifecycle
                        val n by count.collectAsStateWithLifecycle()
                        Text("drawer $n")
                    }
                    entry<Other> { Text("other") }
                },
            )
        }
        stack += Drawer
        rule.onNodeWithText("drawer 0").assertExists()

        // A screen opened from the drawer, kept open long enough for the drawer to be dropped.
        stack += Other
        rule.mainClock.advanceTimeBy(8_000)
        rule.onNodeWithText("other").assertExists()
        stack.removeAt(stack.lastIndex)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()

        assertTrue(drawerLifecycle!!.currentState.isAtLeast(Lifecycle.State.STARTED))
        count.value = 1
        rule.onNodeWithText("drawer 1").assertExists()
    }
}
