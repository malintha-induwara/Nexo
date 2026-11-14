package com.nexo.app.ui.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nexo.app.ui.designsystem.preview.NexoPreview
import com.nexo.app.ui.designsystem.preview.PreviewData
import com.nexo.app.ui.navigation.ExcelExportKey
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private val backup =
        previewFile("nexo_auto_1.nexobak", Instant.parse("2026-11-14T03:00:00Z"), 1)

    @Test
    fun backupSharesACopy() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                BackupScreen(
                    state = BackupUiState(),
                    photosCopied = null,
                    actions = BackupActions(
                        onBack = { events += "back" },
                        onShareBackup = { events += "share" },
                    ),
                )
            }
        }
        rule.onNodeWithText("Export backup file").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("share", "back"), events)
    }

    @Test
    fun backupDeviceOpensTheHistoryAndPicksAFile() {
        val events = mutableListOf<String>()
        rule.setContent {
            NexoPreview {
                BackupDeviceScreen(
                    state = RestoreUiState(backups = listOf(backup)),
                    message = null,
                    pendingRestore = null,
                    restoreResult = null,
                    onBack = {},
                    onBackup = {},
                    onPickBackupFile = { events += "pick" },
                    onAutoBackupHistory = { events += "history" },
                    onMessageShown = {},
                    onRestore = {},
                    onRestoreDismissed = {},
                    onResultShown = {},
                )
            }
        }
        rule.onNodeWithText("Automatic Backup History").assertIsDisplayed().performClick()
        rule.onNodeWithText("Data Recovery").performClick()

        assertEquals(listOf("history", "pick"), events)
    }

    @Test
    fun autoBackupHistoryRestoresTheChosenBackupOnceConfirmed() {
        val restored = mutableListOf<File>()
        rule.setContent {
            NexoPreview {
                AutoBackupHistoryScreen(
                    state = RestoreUiState(backups = listOf(backup)),
                    restoreResult = null,
                    onBack = {},
                    onDelete = {},
                    onRestore = { restored += it },
                    onResultShown = {},
                )
            }
        }
        rule.onNodeWithText("KB", substring = true).performClick()
        rule.onNodeWithText("Yes").performClick()

        assertEquals(listOf(backup), restored)
    }

    @Test
    fun restoreFileGoesBackWhenDeclined() {
        var back = false
        rule.setContent {
            NexoPreview {
                RestoreFileScreen(
                    state = RestoreUiState(),
                    restoreResult = null,
                    onBack = { back = true },
                    onRestore = {},
                    onResultShown = {},
                )
            }
        }
        rule.onNodeWithText("No").assertIsDisplayed().performClick()

        assertEquals(true, back)
    }

    @Test
    fun excelExportExportsAPeriod() {
        val totals = mutableListOf<Boolean>()
        rule.setContent {
            NexoPreview {
                ExcelExportScreen(
                    key = ExcelExportKey(),
                    state = ExcelExportUiState(ref = PreviewData.ref),
                    onBack = {},
                    onExport = { _, total -> totals += total },
                )
            }
        }
        rule.onNodeWithText("Monthly").assertIsDisplayed().performClick()

        assertEquals(listOf(false), totals)
    }
}
