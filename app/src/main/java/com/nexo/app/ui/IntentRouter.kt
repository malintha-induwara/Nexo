package com.nexo.app.ui

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.nexo.app.core.common.NexoIntents.ACTION_ADD
import com.nexo.app.core.common.NexoIntents.ACTION_BOOKMARKS
import com.nexo.app.core.common.NexoIntents.ACTION_REVIEW
import com.nexo.app.core.common.NexoIntents.ACTION_SEARCH
import com.nexo.app.core.common.NexoIntents.EXTRA_TYPE
import com.nexo.app.core.common.NexoIntents.SCHEME
import com.nexo.app.core.model.EntryType
import com.nexo.app.ui.navigation.BookmarksKey
import com.nexo.app.ui.navigation.EntryKey
import com.nexo.app.ui.navigation.RestoreFileKey
import com.nexo.app.ui.navigation.SearchKey
import com.nexo.app.ui.navigation.SmsQueueKey

// Maps launch intents (shortcuts, notifications, deep links, opened files) to screens.
object IntentRouter {
    fun route(intent: Intent?): List<NavKey> {
        intent ?: return emptyList()
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == SCHEME &&
            intent.data?.host == "inoutadd"
        ) {
            return listOf(EntryKey(external = true))
        }
        // Opening a backup file with Nexo asks to restore it.
        if (intent.action == Intent.ACTION_VIEW &&
            (intent.data?.scheme == "content" || intent.data?.scheme == "file")
        ) {
            return listOf(RestoreFileKey(intent.data.toString()))
        }
        return when (intent.action) {
            ACTION_ADD -> listOf(
                EntryKey(
                    type = intent.getStringExtra(EXTRA_TYPE)?.let {
                        runCatching { EntryType.valueOf(it) }.getOrNull()
                    },
                    external = true,
                ),
            )

            ACTION_SEARCH -> listOf(SearchKey(fromNotification = true))

            ACTION_BOOKMARKS -> listOf(BookmarksKey())

            ACTION_REVIEW -> listOf(SmsQueueKey)

            else -> emptyList()
        }
    }
}
