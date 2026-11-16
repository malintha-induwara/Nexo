package com.nexo.app.ui.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.transactionlist.BulkEditActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountDetailScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheStatementAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                AccountDetailScreen(
                    ui = previewAccountDetailState,
                    periods = PreviewData.ref.settings.periods,
                    deleted = false,
                    isNone = false,
                    actions = AccountDetailActions(
                        onBack = { events += "back" },
                        onEditAccount = { events += "edit $it" },
                        onAddTransaction = { id, date -> events += "add $id $date" },
                    ),
                    bulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> }),
                )
            }
        }
        rule.onNodeWithContentDescription("Edit").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Add transaction").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("edit bank", "add bank null", "back"), events)
    }
}
