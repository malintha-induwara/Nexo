package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.StoredPhoto
import java.io.File

/** Transaction photos: files in the private photo folder, shared between rows that attach the same picture. */
interface PhotosRepository {
    fun getPhotoFile(fileName: String): File

    /** Whether the picked file looks like an image type Nexo stores; unknown types are allowed. */
    fun isSupported(uri: Uri): Boolean

    fun displayName(uri: Uri): String?

    /** Copies [uri] into the photo folder, reusing an identical stored file. */
    suspend fun importPhoto(uri: Uri): StoredPhoto?

    /** Removes files that no photo row references any more. */
    suspend fun deleteUnreferenced(fileNames: Collection<String>)

    /** Saves a copy into the shared Pictures/Nexo folder. */
    suspend fun exportToGallery(fileName: String): Boolean
}
