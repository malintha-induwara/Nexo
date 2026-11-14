package com.nexo.app.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
class OnboardingScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: OnboardingUiState, askPermission: Boolean = false) = rule.setContent {
        NexoPreview {
            OnboardingScreen(
                state = state,
                askPermission = askPermission,
                onNext = { events += "next" },
                onBack = { events += "back" },
                onStartFresh = { events += "fresh" },
                onChooseBackup = { events += "backup" },
                onPickCurrency = { events += "currency ${it.code}" },
                onClearError = {},
                onAllowNotifications = { events += "notifications" },
            )
        }
    }

    @Test
    fun welcomeStarts() {
        show(OnboardingUiState())
        rule.onNodeWithText("Get started").assertIsDisplayed().performClick()

        assertEquals(listOf("next"), events)
    }

    @Test
    fun restoreAsksForTheBackupFile() {
        show(OnboardingUiState(step = 1))
        rule.onNodeWithText("Restore from a backup file").performClick()
        rule.onNodeWithText("Choose backup file").performClick()

        assertEquals(listOf("backup"), events)
    }

    @Test
    fun notificationQuestionReportsTheAnswer() {
        show(OnboardingUiState(step = 1, done = true), askPermission = true)
        rule.onNodeWithText("OK").performClick()

        assertEquals(listOf("notifications"), events)
    }
}
