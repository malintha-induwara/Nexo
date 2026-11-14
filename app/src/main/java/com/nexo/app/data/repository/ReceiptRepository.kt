package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.model.ReceiptRead
import java.time.LocalDate

/** Receipts read on the device: nothing leaves the phone. */
interface ReceiptRepository {
    /** The total, date and merchant found on the receipt photo. Throws [java.io.IOException] when it cannot be read. */
    suspend fun scan(uri: Uri, today: LocalDate = LocalDate.now()): ReceiptRead
}
