package com.nexo.app.ui.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nexo.app.core.model.GroupType
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
class AccountSettingsScreensTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun menuReportsTaps() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                AccountSettingsMenuScreen(
                    state = AccountSettingsUiState(PreviewData.ref),
                    onBack = { events += "back" },
                    onOpenGroups = { events += "groups" },
                    onOpenAccounts = {},
                    onOpenDeletedGroups = {},
                    onOpenDeletedAccounts = {},
                    onOpenTransferExpense = {},
                    onCardAccountingChange = {},
                )
            }
        }
        rule.onNodeWithText("Account Group").performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("groups", "back"), events)
    }

    @Test
    fun groupFormAsksForANameBeforeSaving() {
        val saved = mutableListOf<Pair<String, GroupType>>()
        rule.setContent {
            NexoPreview {
                AccountGroupFormScreen(
                    state = AccountGroupsUiState(previewSettingsRef),
                    groupId = null,
                    onBack = {},
                    onSave = { name, type -> saved += name to type },
                )
            }
        }
        rule.onNodeWithText("Save").performClick()
        rule.onNodeWithText("Please enter the contents").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).performTextInput("Trip")
        rule.onNodeWithText("Save").performClick()

        assertEquals(listOf("Trip" to GroupType.NORMAL), saved)
    }

    @Test
    fun deletedGroupsRestoreAfterConfirming() {
        val restored = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                DeletedGroupsScreen(
                    state = DeletedItemsUiState(previewSettingsRef),
                    onBack = {},
                    onRestore = { restored += it },
                    onPurge = {},
                )
            }
        }
        rule.onNodeWithText("Trip fund (0)").performClick()
        rule.onNodeWithText("Yes").performClick()

        assertEquals(listOf("trip"), restored)
    }

    @Test
    fun totalStatsGoesBack() {
        var back = false
        rule.setContent {
            NexoPreview {
                TotalStatsScreen(
                    ui = previewTotalStatsState,
                    onBack = { back = true },
                    onStep = {},
                    onPickMonth = {},
                )
            }
        }
        rule.onNodeWithText("Total Stats").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(true, back)
    }
}
