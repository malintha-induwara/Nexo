package com.nexo.app.data.repository

import android.net.Uri

/** Copies transaction photos to and from a folder the user picked. */
interface PhotoBackupRepository {
    fun localCount(): Int

    /** Returns how many photos were copied. */
    suspend fun export(tree: Uri, progress: (Int, Int) -> Unit): Int

    /** Returns how many photos were copied. */
    suspend fun import(tree: Uri, progress: (Int, Int) -> Unit): Int
}
