package com.nexo.app.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarksScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun opensReordersAndDeletesBookmarks() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                BookmarksScreen(
                    state = previewBookmarksState,
                    onBack = { events += "back" },
                    onOpen = { events += "open $it" },
                    onMove = { events += "move $it" },
                    onDelete = { events += "delete $it" },
                )
            }
        }
        rule.onNodeWithText("Weekly groceries").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Edit").performClick()
        rule.onAllNodesWithContentDescription("Move down")[0].performClick()
        rule.onNodeWithText("Weekly groceries").performClick()
        rule.onNodeWithContentDescription("Delete").performClick()
        rule.onNodeWithText("Yes").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf(
                "open weekly",
                "move [salary, weekly, topup]",
                "delete [weekly]",
                "back",
            ),
            events,
        )
    }
}
