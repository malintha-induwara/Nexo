package com.nexo.app.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.RepeatType
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepeatListScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun opensAndAddsRepeats() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                RepeatListScreen(
                    state = previewRepeatListState,
                    onBack = { events += "back" },
                    onOpenRule = { events += "open $it" },
                    onAdd = { events += "add $it" },
                    onSetReflect = { events += "reflect $it" },
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("Salary").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Add").performClick()
        rule.onNodeWithText("Every Month").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("open salary", "add ${RepeatType.MONTHLY}", "back"), events)
    }
}
