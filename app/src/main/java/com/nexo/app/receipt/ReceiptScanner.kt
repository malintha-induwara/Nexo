package com.nexo.app.receipt

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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Reads a receipt photo on the device with ML Kit text recognition (Latin script, model bundled in the app) and hands
 * the lines to [ReceiptParser]. Nothing leaves the phone.
 */
@Singleton
class ReceiptScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Throws [IOException] when the photo cannot be opened or decoded. */
    suspend fun scan(uri: Uri, today: LocalDate = LocalDate.now()): ReceiptRead {
        val bitmap = withContext(Dispatchers.IO) { decodeUpright(uri) } ?: throw IOException("Cannot decode the photo")
        val text = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            bitmap.recycle()
        }
        val lines = text.textBlocks.flatMap { it.lines }.map(::toOcrLine)
        return withContext(Dispatchers.Default) { ReceiptParser.read(lines, today) }
    }

    /**
     * The photo at no more than [MAX_SIDE] pixels on its long side (a power-of-two step down, so a 12 MP photo keeps
     * its full size), turned upright from its EXIF orientation so line positions read top to bottom.
     */
    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // A bounds-only decode always returns null; the size lands in [bounds].
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (rotation == 0f) return bitmap
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    }

    /** Centre, size and tilt from the line's corners (top-left, top-right, bottom-right, bottom-left); its box otherwise. */
    private fun toOcrLine(line: Text.Line): OcrLine {
        val corners = line.cornerPoints
        if (corners == null || corners.size != 4) {
            val b = line.boundingBox ?: return OcrLine(line.text, 0f, 0f, 0f, 0f)
            return OcrLine.box(line.text, b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        }
        val (tl, tr, _, bl) = corners
        return OcrLine(
            text = line.text,
            centerX = corners.sumOf { it.x }.toFloat() / 4f,
            centerY = corners.sumOf { it.y }.toFloat() / 4f,
            width = hypot((tr.x - tl.x).toFloat(), (tr.y - tl.y).toFloat()),
            height = hypot((bl.x - tl.x).toFloat(), (bl.y - tl.y).toFloat()),
            angle = Math.toDegrees(atan2((tr.y - tl.y).toDouble(), (tr.x - tl.x).toDouble())).toFloat(),
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
