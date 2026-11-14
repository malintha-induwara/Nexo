package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.StoredPhoto
import com.nexo.app.data.database.dao.PhotoDao
import com.nexo.app.data.file.PhotoFileDataSource
import java.io.File
import javax.inject.Inject

class DefaultPhotosRepository @Inject constructor(
    private val files: PhotoFileDataSource,
    private val photoDao: PhotoDao,
) : PhotosRepository {
    override fun getPhotoFile(fileName: String): File = files.file(fileName)

    override fun isSupported(uri: Uri): Boolean = files.isSupported(uri)

    override fun displayName(uri: Uri): String? = files.displayName(uri)

    override suspend fun importPhoto(uri: Uri): StoredPhoto? {
        val bytes = files.readBytes(uri) ?: return null
        val checksum = PhotoFileDataSource.md5(bytes)
        photoDao.byChecksum(checksum)?.let { existing ->
            if (files.file(
                    existing.fileName,
                ).exists()
            ) {
                return StoredPhoto(existing.fileName, existing.fileSize, checksum)
            }
        }
        return files.write(bytes, files.extensionOf(uri), checksum)
    }

    override suspend fun deleteUnreferenced(fileNames: Collection<String>) {
        for (name in fileNames.toSet()) {
            if (photoDao.countByFileName(name) == 0) files.delete(name)
        }
    }

    override suspend fun exportToGallery(fileName: String): Boolean =
        files.exportToGallery(fileName)
}
