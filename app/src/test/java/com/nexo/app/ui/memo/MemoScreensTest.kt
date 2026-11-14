package com.nexo.app.ui.memo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.Memo
import com.nexo.app.core.model.MemoColor
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
class MemoScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun editorShowsTheMemoAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                MemoEditorScreen(
                    s = MemoEditorUiState(
                        loaded = true,
                        id = "memo",
                        title = "Groceries",
                        content = "Rice",
                        date = PreviewData.today,
                        color = MemoColor.YELLOW,
                    ),
                    onClose = { events += "close" },
                    onTogglePin = { events += "pin" },
                    onTitleChange = {},
                    onContentChange = {},
                    onDateChange = {},
                    onColorChange = {},
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("Groceries").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pin").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("pin", "close"), events)
    }

    @Test
    fun listShowsMemosAndReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                MemoListScreen(
                    state = MemoListUiState(
                        ref = PreviewData.ref,
                        date = PreviewData.today,
                        period = PreviewData.ref.settings.periods.monthPeriodContaining(
                            PreviewData.today,
                        ),
                        device = PreviewData.device,
                        memos = listOf(
                            Memo(
                                id = "rent",
                                title = "Rent",
                                content = "",
                                date = PreviewData.today.toString(),
                                dateTime = 0,
                                orderSeq = 0,
                                updatedAt = 0,
                            ),
                        ),
                    ),
                    onBack = { events += "back" },
                    onStep = {},
                    onPickMonth = {},
                    onSetHelpHidden = {},
                    onSetMemoButton = {},
                    onNewMemo = { events += "new $it" },
                    onOpenMemo = { events += "open $it" },
                )
            }
        }
        rule.onNodeWithText("Rent").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("New note").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("open rent", "new ${PreviewData.today}", "back"), events)
    }
}
