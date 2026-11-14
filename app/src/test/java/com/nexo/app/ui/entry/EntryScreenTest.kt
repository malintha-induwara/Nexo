package com.nexo.app.ui.entry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theFormShowsItsFieldsAndReportsTaps() {
        val events = mutableListOf<String>()
        val dialogs = EntryDialogState()
        rule.setContent {
            NexoPreview {
                EntryScreen(
                    state = previewEntryState,
                    actions = EntryActions(
                        close = { events += "close" },
                        setType = { events += "type $it" },
                        save = { events += "save $it" },

                    ),
                    dialogs = dialogs,
                    asDrawer = false,
                    reviewingUnassigned = false,
                    hasCamera = true,
                )
            }
        }
        rule.onNodeWithText("Wallet").assertIsDisplayed()
        rule.onNodeWithText("#work").assertExists()
        rule.onNodeWithContentDescription("Tags").performScrollTo().performClick()
        assertEquals(true, dialogs.tags)
        dialogs.tags = false
        rule.onNodeWithText("Income").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("type ${EntryType.INCOME}", "close"), events)
    }
}
