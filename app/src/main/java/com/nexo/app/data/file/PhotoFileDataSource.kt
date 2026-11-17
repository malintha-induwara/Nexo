package com.nexo.app.data.file

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
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.model.StoredPhoto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

// Photo files in the private folder plus the gallery export; knows nothing about rows.
class PhotoFileDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dirs: AppDirs,
    private val picked: PickedFileDataSource,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    fun file(fileName: String): File = File(dirs.photos, fileName)

    suspend fun files(fileNames: Collection<String>): Map<String, File> = if (fileNames.isEmpty()) {
        emptyMap()
    } else {
        withContext(ioDispatcher) {
            val dir = dirs.photos
            fileNames.associateWith { File(dir, it) }
        }
    }

    suspend fun extensionOf(uri: Uri): String? = picked.extensionOf(uri)

    suspend fun isSupported(uri: Uri): Boolean = extensionOf(uri)?.let { it in SUPPORTED } ?: true

    suspend fun canRead(uri: Uri): Boolean = withContext(ioDispatcher) {
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } }
            .getOrNull() == true
    }

    suspend fun readBytes(uri: Uri): ByteArray? = withContext(ioDispatcher) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    // Downscales sources over 2 MB; [extension] is used for small files only.
    suspend fun write(bytes: ByteArray, extension: String?, checksum: String): StoredPhoto =
        withContext(ioDispatcher) {
            val large = bytes.size > MAX_BYTES
            val name =
                UUID.randomUUID().toString() + "." +
                    (if (large || extension.isNullOrEmpty()) "jpg" else extension)
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
        val bmp =
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                },
            )
                ?: return null
        val longest = maxOf(bmp.width, bmp.height)
        val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
        val rotation = runCatching {
            when (
                ExifInterface(
                    bytes.inputStream(),
                ).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
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

    suspend fun delete(fileName: String) = withContext(ioDispatcher) {
        file(fileName).delete()
    }

    // Saves a copy into the shared Pictures/Nexo folder.
    suspend fun exportToGallery(fileName: String): Boolean = withContext(ioDispatcher) {
        val src = file(fileName)
        if (!src.exists()) return@withContext false
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeFor(fileName))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Nexo",
                    )
                }
            }
            val uri =
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    private fun mimeFor(name: String): String =
        when (name.substringAfterLast('.').lowercase(Locale.ROOT)) {
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

        fun md5(bytes: ByteArray): String =
            MessageDigest.getInstance("MD5").digest(bytes).joinToString("") {
                "%02x".format(it)
            }
    }
}
