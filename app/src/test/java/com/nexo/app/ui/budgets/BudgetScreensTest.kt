package com.nexo.app.ui.budgets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.Budget
import com.nexo.app.domain.BudgetBlockData
import com.nexo.app.domain.BudgetRow
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
class BudgetScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun settingsOpenRealAndVirtualBudgets() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                BudgetSettingsScreen(
                    state = BudgetSettingsUiState(
                        ref = PreviewData.ref,
                        budgets = listOf(Budget("cat_food", "food", 0, updatedAt = 0)),
                        list = BudgetBlockData(
                            total = null,
                            rows = listOf(
                                BudgetRow("cat_food", "food", "Food", 45_000.0, 0.0),
                                BudgetRow(null, "transport", "Transport", 0.0, 0.0),
                            ),
                        ),
                    ),
                    onBack = { events += "back" },
                    onAdd = { events += "add" },
                    onAddBudgetFor = { events += "add $it" },
                    onEditBudget = { events += "edit $it" },
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("Food").assertIsDisplayed().performClick()
        rule.onNodeWithText("Transport").performClick()
        rule.onNodeWithContentDescription("Add").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("edit cat_food", "add transport", "add", "back"), events)
    }
}
