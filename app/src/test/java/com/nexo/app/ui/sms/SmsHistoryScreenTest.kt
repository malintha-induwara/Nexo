package com.nexo.app.ui.sms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsHistoryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsThePastMessagesAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsHistoryScreen(
                    state = previewSmsHistoryState,
                    onBack = { events += "back" },
                    onSetRange = { events += "range $it" },
                    onSetCustom = { _, _ -> },
                    onSetKind = {},
                    onSetCard = {},
                    onToggle = { events += "toggle ${it.item.id}" },
                    onSelectAll = { events += "select ${it.size}" },
                    onAdd = { events += "add" },
                    onMapCard = { _, _ -> },
                    onAnswerFee = {},
                )
            }
        }
        rule.onNodeWithText("Last 7 days").assertIsDisplayed().performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Sample Streaming"))
        rule.onNodeWithText("Sample Streaming").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Select all"))
        rule.onNodeWithText("Select all").performClick()
        rule.onNodeWithText("Add 1 transaction", substring = true).performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(
            listOf("range WEEK", "toggle streaming", "select 3", "add", "back"),
            events,
        )
    }
}
