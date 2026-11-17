package com.nexo.app.core.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object BackupFiles {
    const val EXTENSION = "nexobak"
    const val MIME = "application/vnd.nexo.backup"

    private val accepted = listOf(
        ".$EXTENSION",
        ".mmbak",
        ".sqlite",
        ".MoneyManager",
        ".moneymanager",
        ".MoneyManager2",
        ".moneymanager2",
    )

    // "Nexo(<date>-<HHmmss>).nexobak"; the date part is locale-dependent.
    fun name(now: LocalDateTime, prefix: String = "Nexo"): String {
        val date = now.toLocalDate().format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault()),
        )
            .replace(" ", "")
            .replace(Regex("[^\\p{Nd}]"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        val time = now.format(DateTimeFormatter.ofPattern("HHmmss"))
        return "$prefix($date-$time).$EXTENSION"
    }

    fun isBackupName(name: String): Boolean {
        if (name.contains("weple.sqlite")) return false
        return accepted.any { name.endsWith(it) } || name.endsWith("sql")
    }
}
