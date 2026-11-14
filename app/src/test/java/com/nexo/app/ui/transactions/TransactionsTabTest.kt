package com.nexo.app.ui.transactions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.sms.QueueBadge
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TransactionsTabTest {
    @get:Rule
    val rule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions = TransactionsActions(
        setTab = { events += "tab $it" },
        openSmsQueue = { events += "sms" },
        openSearch = { events += "search" },
        openEntry = { events += "entry $it" },
        addEntry = { events += "add $it" },
        openMemo = { events += "memo $it" },
        exportToExcel = { from, to -> events += "export $from $to" },
        openBudgetSettings = { events += "budgets" },
    )

    private fun show(state: TransactionsUiState) = rule.setContent {
        NexoPreview {
            TransactionsTab(
                state = state,
                smsBadge = QueueBadge(count = 2),
                actions = actions,
                bulkActions = previewBulkActions,
            )
        }
    }

    @Test
    fun dailyShowsTheDaysTransactionsAndReportsTaps() {
        show(previewTransactionsState)

        rule.onNodeWithText("Net").assertIsDisplayed()
        rule.onNodeWithText("Taxi home", substring = true).performClick()
        rule.onNodeWithText("Today", substring = true, ignoreCase = true).performClick()
        rule.onNodeWithContentDescription("2 transactions to review").performClick()
        rule.onNodeWithContentDescription("Search").performClick()
        rule.onNodeWithText("Note").performClick()

        assertEquals(
            listOf("entry t2", "add 2026-11-14", "sms", "search", "tab NOTE"),
            events,
        )
    }

    @Test
    fun notesOpenTheTappedMemo() {
        show(previewTransactionsStateOn(SubTab.NOTE))

        rule.onNodeWithText("Pay the rent before the 20th.").assertIsDisplayed().performClick()

        assertEquals(listOf("memo m1"), events)
    }

    @Test
    fun totalOpensBudgetsAndTheExport() {
        show(previewTransactionsStateOn(SubTab.TOTAL))

        rule.onNodeWithText("Budget Setting").performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Export to Excel"))
        rule.onNodeWithText("Export to Excel").performClick()

        assertEquals(listOf("budgets", "export 2026-11-01 2026-11-30"), events)
    }
}
