package com.nexo.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.nexo.app.core.database.NexoDatabase
import com.nexo.app.core.files.AppDirs
import com.nexo.app.core.files.PhotoStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Photo export / import through a user-picked folder. */
@Singleton
class PhotoTransfer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dirs: AppDirs,
    private val db: NexoDatabase,
) {
    fun localCount(): Int = dirs.photos.listFiles()?.size ?: 0

    private fun children(tree: Uri): List<Triple<String, String, String>> {
        val parent = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = ArrayList<Triple<String, String, String>>()
        context.contentResolver.query(
            parent,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c -> while (c.moveToNext()) out += Triple(c.getString(0), c.getString(1) ?: "", c.getString(2) ?: "") }
        return out
    }

    /** Copies referenced photos that the folder doesn't have yet. */
    suspend fun export(tree: Uri, progress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val referenced = db.photoDao().getAll().map { it.fileName }.toSet()
        val files = dirs.photos.listFiles()?.filter { it.name in referenced }.orEmpty()
        val existing = children(tree).map { it.second }.toSet()
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var done = 0
        files.forEachIndexed { i, f ->
            progress(i + 1, files.size)
            if (f.name in existing) return@forEachIndexed
            val mime = if (f.extension.lowercase(Locale.ROOT) == "png") "image/png" else "image/jpeg"
            val target = DocumentsContract.createDocument(context.contentResolver, dir, mime, f.name) ?: return@forEachIndexed
            context.contentResolver.openOutputStream(target)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            done++
        }
        done
    }

    /** Copies every image file of the folder into the photo folder, overwriting same-named files. */
    suspend fun import(tree: Uri, progress: (Int, Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val images = children(tree).filter { (_, name, _) -> name.substringAfterLast('.', "").lowercase(Locale.ROOT) in PhotoStore.SUPPORTED }
        images.forEachIndexed { i, (id, name, _) ->
            progress(i + 1, images.size)
            val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            context.contentResolver.openInputStream(uri)?.use { input -> File(dirs.photos, name).outputStream().use { input.copyTo(it) } }
        }
        images.size
    }
}
