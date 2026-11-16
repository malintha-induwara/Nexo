package com.nexo.app.ui.entry

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.nexo.app.ui.designsystem.LocalClock
import com.nexo.app.ui.lock.LocalExpectReturn
import java.io.File
import java.time.Clock

/**
 * Scan receipt: the document scanner (rear camera, edges found and cropped, or a photo from the
 * gallery) hands back one page, which is read and then attached once the user confirms it. Where
 * the scanner cannot start (no Google Play services, or a phone it does not support) the plain
 * camera and gallery choices take its place.
 */
@Stable
class ReceiptScanner internal constructor(
    private val context: Context,
    private val clock: Clock,
    private val document: ManagedActivityResultLauncher<IntentSenderRequest, ActivityResult>,
    private val camera: ManagedActivityResultLauncher<Uri, Boolean>,
    private val gallery: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    private val cameraUri: MutableState<String?>,
    private val onUnavailable: State<() -> Unit>,
    private val expectReturn: () -> Unit,
) {
    /** The document scanner; [onUnavailable] offers the camera and gallery instead when it cannot start. */
    fun scan() {
        val activity = context.findActivity()
        if (activity == null) {
            onUnavailable.value()
            return
        }
        receiptScanner().getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                expectReturn()
                runCatching {
                    document.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    onUnavailable.value()
                }
            }
            .addOnFailureListener { onUnavailable.value() }
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "temp").apply { mkdirs() }
        val file = File(dir, "camera_${clock.millis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        cameraUri.value = uri.toString()
        expectReturn()
        camera.launch(uri)
    }

    fun pickPhoto() {
        expectReturn()
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}

/**
 * [onScanned] gets a scanned page or a photo just taken; [onPicked] a file from the gallery, which
 * may turn out not to be an image.
 */
@Composable
fun rememberReceiptScanner(
    onScanned: (Uri) -> Unit,
    onPicked: (Uri) -> Unit,
    onUnavailable: () -> Unit,
): ReceiptScanner {
    val context = LocalContext.current
    val clock = LocalClock.current
    val expectReturn = LocalExpectReturn.current
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    val document =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                GmsDocumentScanningResult.fromActivityResultIntent(r.data)
                    ?.pages?.firstOrNull()?.imageUri?.let(onScanned)
            }
        }
    val cameraUri = rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(RearCameraPicture()) { ok ->
        if (ok) cameraUri.value?.let { onScanned(Uri.parse(it)) }
    }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(onPicked)
        }
    return remember(context, clock, document, camera, gallery, cameraUri, expectReturn) {
        ReceiptScanner(
            context,
            clock,
            document,
            camera,
            gallery,
            cameraUri,
            currentOnUnavailable,
            expectReturn,
        )
    }
}

/**
 * Takes a photo with the phone's camera app, asking it to open on the rear lens. The system has no
 * official way to choose the lens for another app, so these extras are hints: many camera apps
 * follow them, and one that does not simply opens on whichever lens it used last.
 */
private class RearCameraPicture : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            putExtra("android.intent.extras.CAMERA_FACING", 0)
            putExtra("android.intent.extras.LENS_FACING_BACK", 1)
            putExtra("android.intent.extra.USE_FRONT_CAMERA", false)
            putExtra("camerafacing", "rear")
            putExtra("default_camera", "0")
        }
}

/** One page, straightened and cropped, from the rear camera or the gallery. */
private fun receiptScanner() = GmsDocumentScanning.getClient(
    GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build(),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
