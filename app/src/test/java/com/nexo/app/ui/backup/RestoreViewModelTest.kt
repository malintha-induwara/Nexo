package com.nexo.app.ui.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.file.AppDirs
import com.nexo.app.testing.MainDispatcherRule
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import com.nexo.app.ui.HomeState
import java.io.File
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
    private val home = HomeState(fixedClock)
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

    private fun restore() = RestoreViewModel(book.backups, book.repeats, home, fixedClock)

    @Test
    fun automaticBackupsAreListedAndDeleted() = runTest {
        val file = File(dirs.backups, book.backups.backupFileName()).apply { writeText("copy") }
        val vm = restore()
        vm.refreshFiles()
        assertEquals(listOf(file), vm.uiState.value.backups)

        vm.deleteBackups(listOf(file)).join()

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
        assertNull(home.focus.value)

        vm.onResultHandled()
        assertNull(vm.uiState.value.result)
    }
}
