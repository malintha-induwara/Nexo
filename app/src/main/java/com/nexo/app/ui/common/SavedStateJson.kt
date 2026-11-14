package com.nexo.app.ui.common

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.json.Json

/**
 * Input kept in the saved state as one JSON snapshot, so a half-filled form survives Android
 * killing the app in the background. Snapshots that no longer decode are ignored.
 */
inline fun <reified T> SavedStateHandle.getJson(key: String): T? =
    get<String>(key)?.let { runCatching { Json.decodeFromString<T>(it) }.getOrNull() }

inline fun <reified T> SavedStateHandle.setJson(key: String, value: T) {
    set(key, Json.encodeToString(value))
}
