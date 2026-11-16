package com.nexo.app.ui.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.CashFlow
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.transactionlist.BulkEditActions
import com.nexo.app.ui.transactionlist.TxRowUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsResultsAndReportsTaps() {
        val row = TxRowUi(
            id = "t1",
            tx = null,
            category = UiText.Raw("Food"),
            subCategory = null,
            title = UiText.Raw("Lunch with team"),
            titleIsNote = true,
            accountText = UiText.Raw("Wallet"),
            time = null,
            tags = null,
            descriptionLine = null,
            hasPhoto = false,
            flow = CashFlow.EXPENSE,
            amount = 1_850.0,
            amountText = "Rs. 1,850.00",
            amountMain = 1_850.0,
        )
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SearchScreen(
                    state = SearchUiState(
                        ref = PreviewData.ref,
                        criteria = SearchCriteria(anchor = PreviewData.today),
                        result = SearchResult(
                            loaded = true,
                            rows = listOf(PreviewData.today to row),
                        ),
                    ),
                    bulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> }),
                    onBack = { events += "back" },
                    onStep = {},
                    onSetQuery = {},
                    onExecute = {},
                    onUpdateCriteria = {},
                    onToggleSelect = { events += "select $it" },
                    onSetSelection = {},
                    onOpenMemo = {},
                    onOpenTransaction = { events += "open $it" },
                )
            }
        }
        rule.onNodeWithText("Lunch with team").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("open t1", "back"), events)
    }
}
