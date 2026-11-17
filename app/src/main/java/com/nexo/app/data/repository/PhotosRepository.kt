package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.StoredPhoto
import java.io.File

// Files in the private photo folder, shared between rows that attach the same picture.
interface PhotosRepository {
    suspend fun getPhotoFiles(fileNames: Collection<String>): Map<String, File>

    // Unknown types are allowed.
    suspend fun isSupported(uri: Uri): Boolean

    // A picked file's permission ends with the process.
    suspend fun canRead(uri: Uri): Boolean

    // Reuses an identical stored file.
    suspend fun importPhoto(uri: Uri): StoredPhoto?

    suspend fun deleteUnreferenced(fileNames: Collection<String>)

    suspend fun exportToGallery(fileName: String): Boolean
}
