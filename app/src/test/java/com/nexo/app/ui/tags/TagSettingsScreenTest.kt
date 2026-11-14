package com.nexo.app.ui.tags

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagSettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheTagsAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                TagSettingsScreen(
                    state = TagSettingsUiState(tags = PreviewData.tags),
                    onBack = { events += "back" },
                    onStartAdd = { events += "add" },
                    onStartRename = { events += "rename ${it.name}" },
                    onMove = { _, _ -> },
                    onDelete = {},
                    onAdd = {},
                    onRename = {},
                    onConfirmDelete = {},
                    onDismissDialog = {},
                )
            }
        }
        rule.onNodeWithText("#work").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("rename work", "back"), events)
    }
}
