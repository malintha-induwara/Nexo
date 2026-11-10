package com.nexo.app.backup

import java.io.OutputStream
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Cell values understood by [XlsxWriter]. */
sealed interface XCell {
    data class Text(val value: String) : XCell
    data class Num(val value: Double) : XCell
    data class Date(val value: LocalDateTime, val withTime: Boolean) : XCell
}

/** Minimal Office Open XML spreadsheet writer: one sheet, bold header row, date cells. */
object XlsxWriter {
    private val epoch = LocalDateTime.of(1899, 12, 30, 0, 0)

    private fun esc(s: String) = buildString {
        for (c in s) when {
            c == '&' -> append("&amp;")
            c == '<' -> append("&lt;")
            c == '>' -> append("&gt;")
            c == '"' -> append("&quot;")
            c.code < 0x20 && c != '\t' && c != '\n' && c != '\r' -> Unit
            else -> append(c)
        }
    }

    private fun column(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    fun write(out: OutputStream, sheetName: String, header: List<String>, rows: List<List<XCell>>, datePattern: String) {
        val name = sheetName.replace(Regex("[/\\\\?*\\[\\]:]"), "-").take(31).ifBlank { "Sheet1" }
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, body: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(body.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            )
            entry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            )
            entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(name)}" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            )
            // Styles: 0 default, 1 bold header with fill, 2 date, 3 date-time.
            entry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="2"><numFmt numFmtId="164" formatCode="${esc(datePattern)}"/><numFmt numFmtId="165" formatCode="${esc("$datePattern hh:mm:ss")}"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFDDEFEC"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="4"><xf/><xf fontId="1" fillId="2" applyFont="1" applyFill="1"/><xf numFmtId="164" applyNumberFormat="1"/><xf numFmtId="165" applyNumberFormat="1"/></cellXfs></styleSheet>""",
            )
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val w = zip.bufferedWriter(Charsets.UTF_8)
            w.write("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            fun cell(ref: String, c: XCell, style: Int = 0) = when (c) {
                is XCell.Text -> """<c r="$ref" t="inlineStr"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${esc(c.value)}</t></is></c>"""
                is XCell.Num -> """<c r="$ref"><v>${c.value}</v></c>"""
                is XCell.Date -> {
                    val days = ChronoUnit.SECONDS.between(epoch, c.value) / 86400.0
                    """<c r="$ref" s="${if (c.withTime) 3 else 2}"><v>$days</v></c>"""
                }
            }
            w.write("<row r=\"1\">")
            header.forEachIndexed { i, h -> w.write(cell("${column(i)}1", XCell.Text(h), 1)) }
            w.write("</row>")
            rows.forEachIndexed { r, row ->
                val n = r + 2
                w.write("<row r=\"$n\">")
                row.forEachIndexed { i, c -> w.write(cell("${column(i)}$n", c)) }
                w.write("</row>")
            }
            w.write("</sheetData></worksheet>")
            w.flush()
            zip.closeEntry()
        }
    }
}
