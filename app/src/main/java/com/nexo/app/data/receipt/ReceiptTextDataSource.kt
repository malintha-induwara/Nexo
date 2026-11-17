package com.nexo.app.data.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// On-device ML Kit text recognition (Latin script, bundled model).
@Singleton
class ReceiptTextDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Throws IOException when the photo cannot be opened or decoded.
    suspend fun recognize(uri: Uri): List<OcrLine> {
        val bitmap =
            withContext(ioDispatcher) { decodeUpright(uri) }
                ?: throw IOException("Cannot decode the photo")
        val text = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            bitmap.recycle()
        }
        return text.textBlocks.flatMap { it.lines }.map(::toOcrLine)
    }

    // Power-of-two downscale to MAX_SIDE (a 12 MP photo keeps full size), turned upright from EXIF
    // so line positions read top to bottom.
    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; the size lands in [bounds].
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                },
            )
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(
                        stream,
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
            }
        }.getOrNull() ?: 0f
        if (rotation == 0f) return bitmap
        val upright = Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            Matrix().apply {
                postRotate(rotation)
            },
            true,
        )
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    // Corners run top-left, top-right, bottom-right, bottom-left; the bounding box is the fallback.
    private fun toOcrLine(line: Text.Line): OcrLine {
        val corners = line.cornerPoints
        if (corners == null || corners.size != 4) {
            val b = line.boundingBox ?: return OcrLine(line.text, 0f, 0f, 0f, 0f)
            return OcrLine.box(
                line.text,
                b.left.toFloat(),
                b.top.toFloat(),
                b.right.toFloat(),
                b.bottom.toFloat(),
            )
        }
        val (tl, tr, _, bl) = corners
        return OcrLine(
            text = line.text,
            centerX = corners.sumOf { it.x }.toFloat() / 4f,
            centerY = corners.sumOf { it.y }.toFloat() / 4f,
            width = hypot((tr.x - tl.x).toFloat(), (tr.y - tl.y).toFloat()),
            height = hypot((bl.x - tl.x).toFloat(), (bl.y - tl.y).toFloat()),
            angle = Math.toDegrees(
                atan2((tr.y - tl.y).toDouble(), (tr.x - tl.x).toDouble()),
            ).toFloat(),
        )
    }

    private companion object {
        const val MAX_SIDE = 4096
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
