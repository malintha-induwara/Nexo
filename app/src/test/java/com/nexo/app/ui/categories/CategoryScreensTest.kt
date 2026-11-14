package com.nexo.app.ui.categories

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.core.model.CategoryKind
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.CategoryFormKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CategoryScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = CategoryUiState(
        ref = PreviewData.ref.copy(settings = PreviewData.ref.settings.copy(subcategories = true)),
    )

    @Test
    fun listOpensSubcategoriesWhenTheyAreOn() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                CategoryListScreen(
                    kind = CategoryKind.EXPENSE,
                    state = state,
                    onBack = { events += "back" },
                    onAdd = { events += "add" },
                    onOpenSubcategories = { events += "subs $it" },
                    onEditCategory = { events += "edit $it" },
                    onReorder = {},
                    onDelete = {},
                    onSetSubcategories = {},
                )
            }
        }
        rule.onNodeWithText("Transport").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Add").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("subs transport", "add", "back"), events)
    }

    @Test
    fun formSavesTheEditedName() {
        val saved = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                CategoryFormScreen(
                    key = CategoryFormKey(CategoryKind.EXPENSE, categoryId = "transport"),
                    state = state,
                    onBack = {},
                    onSave = { _, id, name, _, _, _ -> saved += "$id $name" },
                    onMoveUnder = { _, _ -> },
                    onMakeMain = {},
                )
            }
        }
        rule.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(listOf("transport Transport"), saved)
    }
}
