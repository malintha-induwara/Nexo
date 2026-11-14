package com.nexo.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class HelpScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun helpOpensATopic() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                HelpScreen(onBack = { events += "back" }, onOpenPage = { events += it })
            }
        }
        rule.onNodeWithText("Getting started").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("getting_started", "back"), events)
    }

    @Test
    fun feedbackReportsAMistranslation() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                FeedbackScreen(
                    onBack = {},
                    onOpenPage = { events += it },
                    onSendFeedback = { events += "mail" },
                    onReportMistranslation = { events += "typo" },
                )
            }
        }
        rule.onNodeWithText("Mistranslation/Typo").assertIsDisplayed().performClick()

        assertEquals(listOf("typo"), events)
    }
}
