package com.nexo.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.BackupFiles
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.file.AppDirs
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RestoreViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook
    private lateinit var dirs: AppDirs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        book = TestBook(context, scope, clock = fixedClock)
        dirs = AppDirs(context)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    private fun restore() = RestoreViewModel(book.backups)

    @Test
    fun automaticBackupsAreListedAndDeleted() = runTest {
        val name = BackupFiles.name(LocalDateTime.now(fixedClock))
        val file = File(dirs.backups, name).apply { writeText("copy") }
        val vm = restore()
        vm.refreshFiles().join()
        assertEquals(listOf(file), vm.uiState.value.backups.map { it.file })

        vm.deleteBackups(listOf(name)).join()

        assertFalse(file.exists())
        assertTrue(vm.uiState.value.backups.isEmpty())
        assertNull(vm.uiState.value.result)
    }

    @Test
    fun aFileThatIsNotABackupIsReportedAndMovesNothing() = runTest {
        val notes = File(dirs.temp, "notes.txt").apply { writeText("not a database") }
        val vm = restore()

        vm.restoreFile(notes).join()

        val state = vm.uiState.value
        assertFalse(state.busy)
        assertEquals(BackupResult.Restored(RestoreResult.NotABackup), state.result)

        vm.onResultHandled()
        assertNull(vm.uiState.value.result)
    }

    @Test
    fun aPickedFileNamedLikeABackupWaitsForConfirmation() = runTest {
        val uri = Uri.fromFile(File(dirs.temp, "Nexo(1).nexobak"))
        val vm = restore()

        vm.pickRestoreFile(uri).join()

        assertEquals(BackupResult.RestorePicked(uri), vm.uiState.value.result)
    }

    @Test
    fun aPickedFileThatIsNoBackupIsTurnedAway() = runTest {
        val vm = restore()

        vm.pickRestoreFile(Uri.fromFile(File(dirs.temp, "holiday.jpg"))).join()

        assertEquals(BackupResult.NotABackup, vm.uiState.value.result)
    }
}
