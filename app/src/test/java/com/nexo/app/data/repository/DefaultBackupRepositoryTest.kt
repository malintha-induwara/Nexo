package com.nexo.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nexo.app.testing.TestBook
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
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

    @Before
    fun setUp() {
        book = TestBook(ApplicationProvider.getApplicationContext<Context>(), scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        book.close()
    }

    @Test
    fun backupFilesUseTheNexoExtension() {
        val name = book.backups.backupFileName(LocalDateTime.of(2026, 11, 14, 9, 5, 7))
        assertTrue(name, name.startsWith("Nexo("))
        assertTrue(name, name.endsWith("-090507).nexobak"))
        assertTrue(book.backups.isBackupName(name))
    }

    @Test
    fun onlyKnownBackupFilesAreOffered() {
        assertTrue(book.backups.isBackupName("old.mmbak"))
        assertTrue(book.backups.isBackupName("export.sqlite"))
        assertFalse(book.backups.isBackupName("weple.sqlite"))
        assertFalse(book.backups.isBackupName("photo.jpg"))
    }
}
