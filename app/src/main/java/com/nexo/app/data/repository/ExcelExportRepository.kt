package com.nexo.app.data.repository

import com.nexo.app.core.common.period.DateRange
import java.io.File

/** Spreadsheet export of the ledger. */
interface ExcelExportRepository {
    /** Writes the rows of [range] to an .xlsx file in the temp folder; [total] names it for the whole book. */
    suspend fun export(range: DateRange, total: Boolean): File
}
