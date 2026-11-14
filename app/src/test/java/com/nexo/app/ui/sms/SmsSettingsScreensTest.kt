package com.nexo.app.ui.sms

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.core.model.FeeMode
import com.nexo.app.ui.designsystem.preview.NexoPreview
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmsSettingsScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun settingsOpenTheirScreens() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsSettingsScreen(
                    state = previewSmsSettingsState,
                    dialog = null,
                    onDialogChange = {},
                    actions = SmsSettingsActions(
                        onBack = { events += "back" },
                        onOpenQueue = { events += "queue" },
                        onOpenCards = { events += "cards" },
                        onOpenSenders = { events += "senders" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Review queue").assertIsDisplayed().performClick()
        rule.onNodeWithText("Cards and accounts").performScrollTo().performClick()
        rule.onNodeWithText("Bank sender IDs").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("queue", "cards", "senders", "back"), events)
    }

    @Test
    fun turningCaptureOnWithoutPermissionAsksFirst() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                var dialog by remember { mutableStateOf<SmsSettingsDialog?>(null) }
                SmsSettingsScreen(
                    state = previewSmsSettingsState.copy(
                        device = previewSmsSettingsState.device?.copy(smsCaptureEnabled = false),
                        permitted = false,
                    ),
                    dialog = dialog,
                    onDialogChange = { dialog = it },
                    actions = SmsSettingsActions(
                        onSetEnabled = { events += "enabled $it" },
                        onRequestPermission = { events += "permission" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Auto-capture bank SMS").performClick()
        rule.onNodeWithText("Read bank SMS?").assertIsDisplayed()
        rule.onNodeWithText("Continue").performClick()

        assertEquals(listOf("permission"), events)
        rule.onNodeWithText("Read bank SMS?").assertDoesNotExist()
    }

    @Test
    fun cardsCanBeRemapped() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsCardsScreen(
                    state = previewSmsSettingsState,
                    onBack = {},
                    onMapSource = { s, id -> events += "map ${s.last4} $id" },
                    onRemoveSource = { events += "remove ${it.last4}" },
                )
            }
        }
        rule.onNodeWithText("Card ending 1234").assertIsDisplayed().performClick()
        rule.onNodeWithText("Wallet").performClick()
        rule.onAllNodesWithContentDescription("Remove")[0].performClick()

        assertEquals(listOf("map 1234 cash", "remove 1234"), events)
    }

    @Test
    fun feeAnswersCanBeChanged() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsFeesScreen(
                    state = previewSmsSettingsState,
                    onBack = {},
                    onSetFeeMode = { p, mode -> events += "${p.route} $mode" },
                )
            }
        }
        rule.onNodeWithText("Foreign currency card payments").assertIsDisplayed().performClick()
        rule.onNodeWithText("Never ask").performClick()

        assertEquals(listOf("FX_CARD_PURCHASE ${FeeMode.NEVER}"), events)
    }

    @Test
    fun sendersCanBeRemoved() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SmsSendersScreen(
                    state = previewSmsSettingsState,
                    onBack = {},
                    onAddSender = { events += "add $it" },
                    onRemoveSender = { events += "remove $it" },
                )
            }
        }
        rule.onNodeWithText("MYBANK").assertIsDisplayed()
        rule.onNodeWithContentDescription("Remove").performClick()
        rule.onNodeWithContentDescription("Add sender ID").performClick()
        rule.onNodeWithText("Add sender ID").assertIsDisplayed()

        assertEquals(listOf("remove MYBANK"), events)
    }
}
