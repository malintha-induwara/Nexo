package com.nexo.app.ui

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.navigation.AccountDetailKey
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.SearchKey

/** Maps launch intents (widgets, shortcuts, notifications, deep link) to screens (LU-016). */
object IntentRouter {
    const val ACTION_ADD = "com.nexo.app.action.ADD"
    const val ACTION_SEARCH = "com.nexo.app.action.SEARCH"
    const val ACTION_BOOKMARKS = "com.nexo.app.action.BOOKMARKS"
    const val ACTION_ACCOUNT = "com.nexo.app.action.ACCOUNT"
    const val ACTION_MAIN = "com.nexo.app.action.MAIN"
    const val ACTION_REVIEW = "com.nexo.app.action.REVIEW"
    const val EXTRA_TYPE = "type"
    const val EXTRA_ACCOUNT = "account"
    const val SCHEME = "nexo"

    fun route(intent: Intent?): List<NavKey> {
        intent ?: return emptyList()
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == SCHEME && intent.data?.host == "inoutadd") {
            return listOf(EntryKey(external = true))
        }
        // Opening a backup file with Nexo asks to restore it (CFG-026 "open with").
        if (intent.action == Intent.ACTION_VIEW && (intent.data?.scheme == "content" || intent.data?.scheme == "file")) {
            return listOf(com.nexo.app.ui.navigation.RestoreFileKey(intent.data.toString()))
        }
        return when (intent.action) {
            ACTION_ADD -> listOf(
                EntryKey(type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { EntryType.valueOf(it) }.getOrNull() }, external = true),
            )
            ACTION_SEARCH -> listOf(SearchKey(fromNotification = true))
            ACTION_BOOKMARKS -> listOf(BookmarksKey())
            ACTION_ACCOUNT -> intent.getStringExtra(EXTRA_ACCOUNT)?.let { listOf(AccountDetailKey(it)) }.orEmpty()
            else -> emptyList()
        }
    }
}
