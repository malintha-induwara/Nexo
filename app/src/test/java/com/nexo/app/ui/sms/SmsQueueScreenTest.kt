package com.nexo.app.ui.sms

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.SmsHistoryImport
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
class SmsQueueScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheQueueAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsQueueScreen(
                    state = previewSmsQueueState.copy(
                        device = PreviewData.device.copy(
                            smsHistoryImport = SmsHistoryImport.SKIPPED,
                        ),
                        unmappedCards = emptyList(),
                    ),
                    actions = SmsQueueActions(
                        onBack = { events += "back" },
                        onOpenSettings = { events += "settings" },
                        onOpenItem = { events += "open $it" },
                        onSetFilter = { events += "filter $it" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Corner Grocer").assertIsDisplayed().performClick()
        rule.onNodeWithText("Capture on").performClick()
        rule.onNodeWithText("Denied").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("open grocery", "settings", "filter DENIED", "back"), events)
    }

    @Test
    fun asksForTheAccountOfAnUnknownCard() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsQueueScreen(
                    state = previewSmsQueueState,
                    actions = SmsQueueActions(
                        onChooseHistory = { events += "history" },
                        onMapCard = { card, id -> events += "map ${card.last4} $id" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Choose transactions").performClick()
        rule.onNodeWithText("Choose account").performClick()
        rule.onNodeWithText("Savings").performClick()

        assertEquals(listOf("history", "map 5678 bank"), events)
    }
}
