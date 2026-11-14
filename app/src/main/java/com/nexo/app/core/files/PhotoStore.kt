package com.nexo.app.core.files

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.nexo.app.core.database.NexoDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StoredPhoto(val fileName: String, val size: Long, val checksum: String)

/** Photo files in the private photo folder. */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dirs: AppDirs,
    private val db: NexoDatabase,
) {
    fun file(fileName: String): File = File(dirs.photos, fileName)

    fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    fun extensionOf(uri: Uri): String? {
        val name = displayName(uri)
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (ext != null) return ext
        val mime = context.contentResolver.getType(uri) ?: return null
        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
    }

    fun isSupported(uri: Uri): Boolean = extensionOf(uri)?.let { it in SUPPORTED } ?: true

    /** Copies [uri] into the photo folder, reusing an identical stored file (MD5) and downscaling sources over 2 MB. */
    suspend fun importUri(uri: Uri): StoredPhoto? = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
        val checksum = md5(bytes)
        db.photoDao().byChecksum(checksum)?.let { existing ->
            if (file(existing.fileName).exists()) return@withContext StoredPhoto(existing.fileName, existing.fileSize, checksum)
        }
        val ext = extensionOf(uri)
        val large = bytes.size > MAX_BYTES
        val name = UUID.randomUUID().toString() + "." + (if (large || ext.isNullOrEmpty()) "jpg" else ext)
        val out = file(name)
        if (large) {
            val data = downscale(bytes) ?: bytes
            out.writeBytes(data)
        } else {
            out.writeBytes(bytes)
        }
        StoredPhoto(name, out.length(), checksum)
    }

    private fun downscale(bytes: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
        val rotation = runCatching {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val transformed = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        var quality = 100
        var result: ByteArray
        var attempts = 0
        do {
            val buffer = ByteArrayOutputStream()
            transformed.compress(Bitmap.CompressFormat.JPEG, quality, buffer)
            result = buffer.toByteArray()
            quality -= 3
            attempts++
        } while (result.size > MAX_BYTES && attempts < 5)
        return result
    }

    /** Removes files that no photo row references any more. */
    suspend fun deleteUnreferenced(fileNames: Collection<String>) = withContext(Dispatchers.IO) {
        for (name in fileNames.toSet()) {
            if (db.photoDao().countByFileName(name) == 0) file(name).delete()
        }
    }

    /** Saves a copy into the shared Pictures/Nexo folder. */
    suspend fun exportToGallery(fileName: String): Boolean = withContext(Dispatchers.IO) {
        val src = file(fileName)
        if (!src.exists()) return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(fileName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Nexo")
                }
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } }
            true
        }.getOrDefault(false)
    }

    private fun mimeFor(name: String): String = when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heif"
        "bmp" -> "image/bmp"
        "tif", "tiff" -> "image/tiff"
        else -> "image/jpeg"
    }

    companion object {
        const val MAX_BYTES = 2_097_152
        const val MAX_SIDE = 2048
        val SUPPORTED = setOf("jpg", "jpeg", "png", "bmp", "tif", "tiff", "heic", "heif", "webp")

        fun md5(bytes: ByteArray): String = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
