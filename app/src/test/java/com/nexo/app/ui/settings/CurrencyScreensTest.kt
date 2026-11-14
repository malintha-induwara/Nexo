package com.nexo.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nexo.app.core.model.SymbolPosition
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.SubCurrencyEditKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CurrencyScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun mainCurrencySavesTheFormat() {
        val saved = mutableListOf<Pair<SymbolPosition, Int>>()
        rule.setContent {
            NexoPreview {
                MainCurrencyScreen(
                    state = MainCurrencyUiState(ref = PreviewData.ref),
                    onBack = {},
                    onChange = {},
                    onSave = { position, decimals -> saved += position to decimals },
                )
            }
        }
        rule.onNodeWithText("Sri Lankan Rupee").assertIsDisplayed()
        rule.onNodeWithText("Save").performScrollTo().performClick()

        assertEquals(listOf(SymbolPosition.PREFIX to 2), saved)
    }

    @Test
    fun subCurrenciesOpenOneAndAddAnother() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                SubCurrenciesScreen(
                    state = SubCurrencyUiState(ref = PreviewData.ref),
                    onBack = {},
                    onAdd = { events += "add" },
                    onEdit = { events += it.id },
                    onReorder = {},
                    onHide = {},
                )
            }
        }
        rule.onNodeWithText("US Dollar").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Add").performClick()

        assertEquals(listOf("USD", "add"), events)
    }

    @Test
    fun customCurrencyNeedsAName() {
        var saves = 0
        rule.setContent {
            NexoPreview {
                SubCurrencyEditScreen(
                    key = SubCurrencyEditKey(custom = true),
                    state = SubCurrencyUiState(ref = PreviewData.ref),
                    onBack = {},
                    onSave = { _, _, _, _, _, _, _ -> saves++ },
                )
            }
        }
        rule.onNodeWithText("Save").performScrollTo().performClick()

        rule.onNodeWithText("Please enter the name.").performScrollTo().assertIsDisplayed()
        assertEquals(0, saves)
    }
}
