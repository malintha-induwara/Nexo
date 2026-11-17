package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.DateOrder
import com.nexo.app.core.model.ImportPreview
import com.nexo.app.core.model.StoredFile
import java.io.File

// Tab-separated files waiting in the import folder, their preview, and saving them.
interface TransactionImportRepository {
    // Newest first.
    suspend fun getFiles(): List<StoredFile>

    fun file(name: String): File

    // Copies a picked file into the import folder; null when it can't be used.
    suspend fun add(uri: Uri): File?

    suspend fun removeLine(fileName: String, index: Int)

    // Used when a file doesn't say.
    fun deviceDateOrder(): DateOrder

    suspend fun load(fileName: String, order: DateOrder?): ImportPreview

    // Saves every valid row; returns (saved, failed).
    suspend fun saveAll(fileName: String, order: DateOrder?): Pair<Int, Int>

    suspend fun delete(names: Collection<String>)
}
