package com.nexo.app.core.model

import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFilesTest {
    @Test
    fun backupFilesUseTheNexoExtension() {
        val name = BackupFiles.name(LocalDateTime.of(2026, 11, 14, 9, 5, 7))
        assertTrue(name, name.startsWith("Nexo("))
        assertTrue(name, name.endsWith("-090507).nexobak"))
        assertTrue(BackupFiles.isBackupName(name))
    }

    @Test
    fun onlyKnownBackupFilesAreOffered() {
        assertTrue(BackupFiles.isBackupName("old.mmbak"))
        assertTrue(BackupFiles.isBackupName("export.sqlite"))
        assertFalse(BackupFiles.isBackupName("weple.sqlite"))
        assertFalse(BackupFiles.isBackupName("photo.jpg"))
    }
}
