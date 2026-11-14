package com.nexo.app.ui.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountFormScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheAccountAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                AccountFormScreen(
                    previewAccountFormState,
                    AccountFormActions(
                        onBack = { events += "back" },
                        onSave = { events += "save $it" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Account Info").assertIsDisplayed()
        rule.onNodeWithText("Save").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("save false", "back"), events)
    }
}
