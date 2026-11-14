package com.nexo.app.ui.stats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.nexo.app.ui.designsystem.UiText
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.StatsDetailKind
import com.nexo.app.ui.transactions.BulkEditActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val range = PreviewData.ref.settings.periods.monthPeriodContaining(
        PreviewData.today,
    ).range

    @Test
    fun tabOpensATappedCategory() {
        val opened = mutableListOf<String?>()
        rule.setContent {
            NexoPreview {
                StatsTab(
                    state = StatsUiState(
                        ref = PreviewData.ref,
                        query = StatsQuery(anchor = PreviewData.today),
                        data = StatsData(
                            range = range,
                            expense = listOf(
                                StatsRow(
                                    "food",
                                    UiText.Raw("Food"),
                                    30.0,
                                    75.0,
                                    0,
                                    StatsDetailKind.EXPENSE,
                                ),
                                StatsRow(
                                    "transport",
                                    UiText.Raw("Transport"),
                                    10.0,
                                    25.0,
                                    1,
                                    StatsDetailKind.EXPENSE,
                                ),
                            ),
                        ),
                    ),
                    onStep = {},
                    onSetRange = { _, _ -> },
                    onSetIncome = {},
                    onSetScope = {},
                    onSetMonth = {},
                    onSetAnchor = {},
                    onOpenRow = { opened += it.key },
                )
            }
        }
        // The ring sits above the cards, so the second card starts below a small test screen.
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Transport"))
        rule.onNodeWithText("Transport").assertIsDisplayed().performClick()

        assertEquals(listOf("transport"), opened)
    }

    @Test
    fun detailSelectsABreakdownAndGoesBack() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                StatsDetailScreen(
                    kind = StatsDetailKind.EXPENSE,
                    ui = StatsDetailUiState(
                        ref = PreviewData.ref,
                        query = StatsQuery(anchor = PreviewData.today),
                        title = UiText.Raw("Food"),
                        range = range,
                        total = 30.0,
                        breakdown = listOf(
                            Breakdown("all", UiText.Raw("All"), 30.0, null),
                            Breakdown("groceries", UiText.Raw("Groceries"), 30.0, 100),
                        ),
                    ),
                    bulkActions = BulkEditActions({}, {}, {}, {}, {}, { _, _ -> }),
                    onBack = { events += "back" },
                    onStep = {},
                    onSetRange = { _, _ -> },
                    onPickMonth = {},
                    onSelectBreakdown = { events += "select $it" },
                    onToggleSelect = {},
                    onSetSelection = {},
                    onOpenTransaction = {},
                    onOpenMemos = {},
                )
            }
        }
        rule.onNodeWithText("Groceries").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("select groceries", "back"), events)
    }
}
