package com.nexo.app.core.model

import java.io.File

/** A file in one of the app's folders, with what a list shows about it read up front. */
data class StoredFile(val file: File, val modifiedAt: Long, val sizeBytes: Long) {
    val name: String get() = file.name

    companion object {
        fun of(file: File) = StoredFile(file, file.lastModified(), file.length())
    }
}
