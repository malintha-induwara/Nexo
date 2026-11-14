package com.nexo.app.data.repository

import android.net.Uri
import com.nexo.app.core.common.Dispatcher
import com.nexo.app.core.common.NexoDispatchers
import com.nexo.app.core.model.ReceiptRead
import com.nexo.app.data.receipt.ReceiptParser
import com.nexo.app.data.receipt.ReceiptTextDataSource
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class DefaultReceiptRepository @Inject constructor(
    private val text: ReceiptTextDataSource,
    @Dispatcher(NexoDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) : ReceiptRepository {
    override suspend fun scan(uri: Uri, today: LocalDate): ReceiptRead {
        val lines = text.recognize(uri)
        return withContext(defaultDispatcher) { ReceiptParser.read(lines, today) }
    }
}
