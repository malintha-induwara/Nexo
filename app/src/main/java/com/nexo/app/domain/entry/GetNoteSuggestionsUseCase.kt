package com.nexo.app.domain.entry

import com.nexo.app.core.common.period.toIsoText
import com.nexo.app.core.model.NoteUsage
import com.nexo.app.data.repository.ReferenceDataRepository
import com.nexo.app.data.repository.TransactionsRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

// Notes used within about 15 months and since autocomplete was last reset; empty when the book
// has autocomplete off.
class GetNoteSuggestionsUseCase @Inject constructor(
    private val refs: ReferenceDataRepository,
    private val transactions: TransactionsRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): List<NoteUsage> {
        val settings = refs.getRefData().settings
        if (!settings.autocomplete) return emptyList()
        val cutoff = maxOf(clock.millis() - WINDOW_MS, settings.autocompleteResetAt)
        val cutDate = Instant.ofEpochMilli(cutoff).atZone(clock.zone).toLocalDate().toIsoText()
        return transactions.getNoteUsage(cutDate, cutoff)
    }

    private companion object {
        const val WINDOW_MS = 456L * 24 * 3600 * 1000
    }
}
