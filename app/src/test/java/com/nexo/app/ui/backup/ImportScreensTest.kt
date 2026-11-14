package com.nexo.app.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.core.model.ImportError
import com.nexo.app.core.model.ImportErrorType
import com.nexo.app.core.model.ImportPreview
import com.nexo.app.core.model.ImportRow
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun importListOpensAFileAndTheHelp() {
        val events = mutableListOf<String>()
        val file = previewFile("november.tsv", Instant.parse("2026-11-14T05:10:00Z"), 12_000)
        rule.setContent {
            NexoPreview {
                ImportListScreen(
                    state = ImportUiState(imports = listOf(file)),
                    onBack = {},
                    onHelp = { events += "help" },
                    onPickFile = {},
                    onOpen = { events += it },
                    onDelete = {},
                )
            }
        }
        rule.onNodeWithText("november.tsv").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Help").performClick()

        assertEquals(listOf("november.tsv", "help"), events)
    }

    @Test
    fun importPreviewOffersToAddAMissingCategory() {
        val added = mutableListOf<Pair<CategoryKind, String?>>()
        val preview = ImportPreview(
            rows = listOf(
                ImportRow(
                    index = 2,
                    raw = "2026-11-13\tExpense\tWallet\tPets\t\t1200",
                    cells = listOf("2026-11-13", "Expense", "Wallet", "Pets", "", "1200"),
                    error = ImportError(ImportErrorType.EXPENSE_CATEGORY, name = "Pets"),
                    parsed = null,
                ),
            ),
            wide = false,
        )
        rule.setContent {
            NexoPreview {
                ImportPreviewScreen(
                    state = ImportUiState(ref = PreviewData.ref, preview = preview),
                    actions = ImportPreviewActions(
                        onAddCategory = { kind, name -> added += kind to name },
                    ),
                )
            }
        }
        rule.onNodeWithText("Pets", substring = true).assertIsDisplayed().performClick()
        rule.onNodeWithText("Add : Pets").performClick()

        assertEquals(listOf(CategoryKind.EXPENSE to "Pets"), added)
    }
}
