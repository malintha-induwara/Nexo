package com.nexo.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.sms.QueueBadge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainShellTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun switchesTabsAndReportsTheAddButton() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                MainShell(
                    state = MainShellUiState(QueueBadge(count = 3)),
                    onAddTransaction = { events += "add" },
                    onOpenSmsQueue = { events += "queue" },
                    tabContent = { tab, signal -> Text("page $tab $signal") },
                )
            }
        }
        rule.onNodeWithText("page TRANSACTIONS 0").assertIsDisplayed()
        rule.onNodeWithText("Stats").performClick()
        rule.onNodeWithText("page STATS 0").assertIsDisplayed()
        rule.onNodeWithText("Stats").performClick()
        rule.onNodeWithText("page STATS 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Add transaction").performClick()

        assertEquals(listOf("add"), events)
    }
}
