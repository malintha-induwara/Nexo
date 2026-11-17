package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.ReceiptRead

// Receipts are read on the device; nothing leaves the phone.
interface ReceiptRepository {
    // Throws [java.io.IOException] when the photo cannot be read.
    suspend fun scan(uri: Uri): ReceiptRead
}
