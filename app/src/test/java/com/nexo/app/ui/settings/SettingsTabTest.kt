package com.nexo.app.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.ConfigurationKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsTabTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun opensAHubItemAndLocksNow() {
        val events = mutableListOf<Any>()
        rule.setContent {
            NexoPreview {
                SettingsTab(
                    state = SettingsUiState(PreviewData.device.copy(pinHash = "hash")),
                    onOpen = { events += it },
                    onLockNow = { events += "lock" },
                    onSendFeedback = {},
                )
            }
        }
        rule.onNodeWithText("Configuration").performClick()
        rule.onNodeWithContentDescription("Lock now").performClick()

        assertEquals(listOf(ConfigurationKey, "lock"), events)
    }
}
