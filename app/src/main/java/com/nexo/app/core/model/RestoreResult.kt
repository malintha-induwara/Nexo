package com.nexo.app.core.model

enum class BackupFormat { NEXO, LEGACY_ANDROID, LEGACY_IPHONE, UNKNOWN }

sealed interface RestoreResult {
    data object Success : RestoreResult
    data object NotABackup : RestoreResult
    data object Unsupported : RestoreResult
    data class Failed(val message: String?) : RestoreResult
}
