package com.nexo.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nexo.app.core.common.ApplicationScope
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.common.runToCompletion
import com.nexo.app.data.database.NexoDatabase
import com.nexo.app.data.file.AppDirs
import com.nexo.app.data.file.PhotoFileDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

class DefaultPhotoBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dirs: AppDirs,
    private val db: NexoDatabase,
    @Dispatcher(NexoDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) : PhotoBackupRepository {
    private fun children(tree: Uri): List<Triple<String, String, String>> {
        val parent = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val out = ArrayList<Triple<String, String, String>>()
        context.contentResolver.query(
            parent,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                out +=
                    Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "")
            }
        }
        return out
    }

    // Copies referenced photos the folder doesn't have yet.
    override suspend fun export(tree: Uri, progress: (Int, Int) -> Unit): Int =
        scope.runToCompletion { copyOut(tree, progress) }

    // Runs to completion: a cut-short copy leaves an empty file that looks like the photo.
    private suspend fun copyOut(tree: Uri, progress: (Int, Int) -> Unit): Int =
        withContext(ioDispatcher) {
            val referenced = db.photoDao().getAll().map { it.fileName }.toSet()
            val files = dirs.photos.listFiles()?.filter { it.name in referenced }.orEmpty()
            val existing = children(tree).map { it.second }.toSet()
            val dir = DocumentsContract.buildDocumentUriUsingTree(
                tree,
                DocumentsContract.getTreeDocumentId(tree),
            )
            var done = 0
            files.forEachIndexed { i, f ->
                progress(i + 1, files.size)
                if (f.name in existing) return@forEachIndexed
                val mime = if (f.extension.lowercase(Locale.ROOT) == "png") {
                    "image/png"
                } else {
                    "image/jpeg"
                }
                val target =
                    DocumentsContract.createDocument(context.contentResolver, dir, mime, f.name)
                        ?: return@forEachIndexed
                context.contentResolver.openOutputStream(target)?.use { out ->
                    f.inputStream().use { it.copyTo(out) }
                }
                done++
            }
            done
        }

    // Copies every image in the folder into the photo folder, overwriting same-named files.
    override suspend fun import(tree: Uri, progress: (Int, Int) -> Unit): Int =
        scope.runToCompletion { copyIn(tree, progress) }

    private suspend fun copyIn(tree: Uri, progress: (Int, Int) -> Unit): Int =
        withContext(ioDispatcher) {
            val images = children(tree).filter { (_, name, _) ->
                name.substringAfterLast('.', "").lowercase(Locale.ROOT) in
                    PhotoFileDataSource.SUPPORTED
            }
            images.forEachIndexed { i, (id, name, _) ->
                progress(i + 1, images.size)
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    File(dirs.photos, name).outputStream().use { input.copyTo(it) }
                }
            }
            images.size
        }
}
