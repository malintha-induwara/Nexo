package com.nexo.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.core.model.ThemeMode
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
class AppearanceScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun styleReportsTheChosenTheme() {
        val events = mutableListOf<Any>()
        rule.setContent {
            NexoPreview {
                StyleScreen(
                    state = AppearanceUiState(PreviewData.device),
                    onBack = { events += "back" },
                    onThemeMode = { events += it },
                )
            }
        }
        rule.onNodeWithText("Dark Mode").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf(ThemeMode.DARK, "back"), events)
    }

    @Test
    fun reminderTurnsTheAlarmOn() {
        val events = mutableListOf<Boolean>()
        rule.setContent {
            NexoPreview {
                ReminderScreen(
                    state = ReminderUiState(PreviewData.device),
                    is24Hour = false,
                    canScheduleExactAlarms = { true },
                    onBack = {},
                    onAlarm = { events += it },
                    onSetReminder = { _, _, _, _ -> },
                    onOpenExactAlarmSettings = {},
                )
            }
        }
        rule.onNodeWithText("Alarm").assertIsDisplayed().performClick()

        assertEquals(listOf(true), events)
    }

    @Test
    fun lockSettingsLocksNow() {
        var locked = false
        rule.setContent {
            NexoPreview {
                LockSettingsScreen(
                    state = LockSettingsUiState(PreviewData.device.copy(pinHash = "hash")),
                    biometricsAvailable = false,
                    onBack = {},
                    onBiometrics = {},
                    onLockTimeout = {},
                    onSetPin = {},
                    isCurrentPin = { false },
                    onLockNow = { locked = true },
                )
            }
        }
        rule.onNodeWithText("Change PIN").assertIsDisplayed()
        rule.onNodeWithText("Lock now").performScrollTo().performClick()

        assertEquals(true, locked)
    }
}
