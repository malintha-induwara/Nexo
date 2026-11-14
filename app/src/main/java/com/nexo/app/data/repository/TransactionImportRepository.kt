package com.nexo.app.data.repository

import android.net.Uri
import java.io.File

/** Tab-separated transaction files waiting in the import folder, their preview, and saving them to the ledger. */
interface TransactionImportRepository {
    fun files(): List<File>

    fun file(name: String): File

    /** Copies a picked file into the import folder; null when it can't be read. */
    suspend fun add(uri: Uri): File?

    suspend fun removeLine(fileName: String, index: Int)

    /** The device's date order (day-month-year and so on), used when a file doesn't say. */
    fun deviceDateOrder(): DateOrder

    suspend fun load(fileName: String, order: DateOrder?): ImportPreview

    /** Saves every valid row; returns (saved, failed). */
    suspend fun saveAll(fileName: String, order: DateOrder?): Pair<Int, Int>

    suspend fun delete(names: Collection<String>)
}
