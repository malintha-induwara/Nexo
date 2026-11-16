package com.nexo.app.data.file

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** What the system knows about a file the user picked. */
class PickedFileDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun displayName(uri: Uri): String? = withContext(ioDispatcher) {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }

    /** The file's extension in lower case, from its name or else its type. */
    suspend fun extensionOf(uri: Uri): String? = withContext(ioDispatcher) {
        val ext = displayName(uri)?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        ext ?: context.contentResolver.getType(uri)?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
    }
}
