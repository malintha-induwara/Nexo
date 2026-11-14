package com.nexo.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.WeekendRule
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
class ConfigurationScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = ConfigurationUiState(ref = PreviewData.ref, device = PreviewData.device)

    @Test
    fun configurationReportsNavigation() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                ConfigurationScreen(
                    state = state,
                    languageTag = "",
                    actions = ConfigurationActions(
                        onBack = { events += "back" },
                        onAccountGroups = { events += "groups" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Account Group").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("groups", "back"), events)
    }

    @Test
    fun monthStartUpdatesTheWeekendRule() {
        var settings = BookSettings()
        rule.setContent {
            NexoPreview {
                MonthStartScreen(
                    state = state,
                    onBack = {},
                    onUpdate = { settings = it(settings) },
                )
            }
        }
        rule.onNodeWithText("Following Monday").assertIsDisplayed()
        // The radio buttons run None, Previous Friday, Following Monday.
        rule.onAllNodes(isSelectable())[2].performClick()

        assertEquals(WeekendRule.FOLLOWING_MONDAY, settings.weekendRule)
    }
}
