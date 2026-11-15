package com.nexo.app.data.repository

import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Device-only preferences: look, reminders, lock, SMS capture state. Never part of a data backup. */
interface UserPreferencesRepository {
    fun getDeviceSettingsStream(): Flow<DeviceSettings>

    suspend fun getDeviceSettings(): DeviceSettings

    suspend fun setSetupDone(v: Boolean)

    suspend fun setThemeMode(v: ThemeMode)

    suspend fun setNumberPadDescending(v: Boolean)

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>)

    suspend fun setCopyWithToday(v: Boolean)

    suspend fun setGroupedPicker(v: Boolean)

    suspend fun setPhotoNoticeShown()

    suspend fun setWhatsNewVersion(v: Int)

    suspend fun setCollapsedGroups(v: Set<String>)

    suspend fun setLastAutoBackup(v: Long)

    suspend fun setRepeatDay(v: String)

    suspend fun setPin(hash: String?)

    suspend fun setPinFailures(v: Int, lockoutUntil: Long = 0)

    suspend fun setBiometrics(v: Boolean)

    suspend fun setLockTimeout(v: LockTimeout)

    suspend fun setPermissionIntroShown()

    suspend fun setTransferGuideShown()

    suspend fun setMemoHelpHidden(v: Boolean)

    suspend fun setHideAmounts(v: Boolean)

    suspend fun setPrivacyButton(v: Boolean)

    suspend fun setSmsCaptureEnabled(v: Boolean)

    suspend fun markSmsCaptureStarted(now: Long)

    suspend fun markSmsCaptureStopped(now: Long)

    suspend fun setSmsLastProcessed(v: Long)

    suspend fun setSmsHistoryImport(v: SmsHistoryImport)

    suspend fun setSmsStoreFullText(v: Boolean)

    suspend fun setSmsCustomSenders(v: Set<String>)

    suspend fun setSmsSeenAt(v: Long)

    suspend fun setFxFetchedAt(v: Long)

    suspend fun clearSmsGap()
}
