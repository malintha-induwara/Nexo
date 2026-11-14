package com.nexo.app.ui.entry

import com.nexo.app.data.repository.TagResult

/** What the tag picker can do, backed by the owning screen's ViewModel. */
class TagActions(
    val add: suspend (raw: String) -> TagResult,
    val rename: suspend (id: String, raw: String) -> TagResult,
    val usageCount: suspend (id: String) -> Int,
    val delete: suspend (id: String) -> Unit,
    val reorder: suspend (ids: List<String>) -> Unit,
)
