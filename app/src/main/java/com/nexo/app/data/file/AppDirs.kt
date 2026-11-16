package com.nexo.app.data.file

import android.content.Context
import com.nexo.app.data.database.NexoDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AppDirs @Inject constructor(@ApplicationContext private val context: Context) {
    private fun dir(name: String) = File(context.filesDir, name).apply { mkdirs() }

    val photos: File get() = dir("photo")
    val backups: File get() = dir("backup")
    val imports: File get() = dir("import")
    val temp: File get() = File(context.cacheDir, "temp").apply { mkdirs() }
    val database: File get() = context.getDatabasePath(NexoDatabase.FILE_NAME)

    fun clearTemp() {
        temp.listFiles()?.forEach { it.deleteRecursively() }
    }
}
