package com.nexo.app.ui.accounts

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
class AccountsTabTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheAccountsAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                AccountsTab(
                    state = previewAccountsState,
                    onSetRange = { _, _ -> },
                    onStep = {},
                    onSetMode = {},
                    onSetAnchor = {},
                    onToggleCollapsed = {},
                    onOpenStats = { events += "stats" },
                    onAddAccount = {},
                    onOpenAccountList = {},
                    onOpenAccount = { id, date -> events += "open $id $date" },
                )
            }
        }
        rule.onNodeWithText("Wallet").assertIsDisplayed()
        rule.onNodeWithText("Savings").performClick()
        rule.onNodeWithContentDescription("Total Stats").performClick()

        assertEquals(listOf("open bank null", "stats"), events)
    }
}
