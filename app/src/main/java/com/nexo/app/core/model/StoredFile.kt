package com.nexo.app.core.model

import java.io.File

// Date and size are read up front for lists.
data class StoredFile(val file: File, val modifiedAt: Long, val sizeBytes: Long) {
    val name: String get() = file.name

    companion object {
        fun of(file: File) = StoredFile(file, file.lastModified(), file.length())
    }
}
