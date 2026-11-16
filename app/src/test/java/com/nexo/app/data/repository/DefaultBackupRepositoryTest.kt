package com.nexo.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.core.model.RestoreResult
import com.nexo.app.data.file.AppDirs
import com.nexo.app.testing.TestBook
import com.nexo.app.testing.fixedClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultBackupRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private lateinit var book: TestBook
    private lateinit var dirs: AppDirs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        book = TestBook(context, scope, clock = fixedClock)
        dirs = AppDirs(context)
        dirs.backups.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    @Test
    fun automaticBackupsAreListedNewestFirstWithTheirSizeAndTime() = runBlocking {
        val older = File(dirs.backups, "Nexo(1).nexobak").apply {
            writeText("1234")
            setLastModified(1_000_000)
        }
        val newer = File(dirs.backups, "Nexo(2).nexobak").apply {
            writeText("12")
            setLastModified(2_000_000)
        }
        File(dirs.backups, "notes.txt").writeText("not a backup")

        val listed = book.backups.getAutoBackups()

        assertEquals(listOf(newer, older), listed.map { it.file })
        assertEquals(listOf(2L, 4L), listed.map { it.sizeBytes })
        assertEquals(listOf(2_000_000L, 1_000_000L), listed.map { it.modifiedAt })
    }

    @Test
    fun deletingAutomaticBackupsRemovesOnlyTheNamedOnes() = runBlocking {
        val gone = File(dirs.backups, "Nexo(1).nexobak").apply { writeText("a") }
        val kept = File(dirs.backups, "Nexo(2).nexobak").apply { writeText("b") }

        book.backups.deleteAutoBackups(listOf(gone.name))

        assertFalse(gone.exists())
        assertTrue(kept.exists())
    }

    @Test
    fun aFileThatIsNoDatabaseIsNotRestored() = runBlocking {
        val notes = File(dirs.temp, "notes.txt").apply { writeText("not a database") }

        assertEquals(RestoreResult.NotABackup, book.backups.restoreFile(notes))
    }
}
