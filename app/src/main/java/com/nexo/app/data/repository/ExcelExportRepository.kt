package com.nexo.app.data.repository

import com.nexo.app.core.common.period.DateRange
import java.io.File

interface ExcelExportRepository {
    // Writes an .xlsx to the temp folder; [total] names it for the whole book.
    suspend fun export(range: DateRange, total: Boolean): File
}
