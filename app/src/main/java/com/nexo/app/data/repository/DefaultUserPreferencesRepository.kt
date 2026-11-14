package com.nexo.app.data.repository

import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.ThemeMode
import com.nexo.app.data.datastore.DevicePreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class DefaultUserPreferencesRepository @Inject constructor(
    private val source: DevicePreferencesDataSource,
) : UserPreferencesRepository {
    override fun getDeviceSettingsStream(): Flow<DeviceSettings> = source.deviceSettings

    override suspend fun getDeviceSettings(): DeviceSettings = source.current()

    override fun getWidgetModeStream(widgetId: Int): Flow<Int> = source.widgetMode(widgetId)

    override suspend fun getWidgetMode(widgetId: Int): Int = source.widgetModeNow(widgetId)

    override suspend fun setWidgetMode(widgetId: Int, mode: Int) {
        source.setWidgetMode(widgetId, mode)
    }

    override suspend fun clearWidgetMode(widgetId: Int) {
        source.clearWidgetMode(widgetId)
    }

    override suspend fun setSetupDone(v: Boolean) {
        source.setSetupDone(v)
    }

    override suspend fun setThemeMode(v: ThemeMode) {
        source.setThemeMode(v)
    }

    override suspend fun setWidgetTheme(v: ThemeMode) {
        source.setWidgetTheme(v)
    }

    override suspend fun setWidgetOpacity(v: Float) {
        source.setWidgetOpacity(v)
    }

    override suspend fun setNumberPadDescending(v: Boolean) {
        source.setNumberPadDescending(v)
    }

    override suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>) {
        source.setReminder(enabled, hour, minute, days)
    }

    override suspend fun setCopyWithToday(v: Boolean) {
        source.setCopyWithToday(v)
    }

    override suspend fun setGroupedPicker(v: Boolean) {
        source.setGroupedPicker(v)
    }

    override suspend fun setPhotoNoticeShown() {
        source.setPhotoNoticeShown()
    }

    override suspend fun setWhatsNewVersion(v: Int) {
        source.setWhatsNewVersion(v)
    }

    override suspend fun setCollapsedGroups(v: Set<String>) {
        source.setCollapsedGroups(v)
    }

    override suspend fun setLastAutoBackup(v: Long) {
        source.setLastAutoBackup(v)
    }

    override suspend fun setRepeatDay(v: String) {
        source.setRepeatDay(v)
    }

    override suspend fun setPin(hash: String?) {
        source.setPin(hash)
    }

    override suspend fun setPinFailures(v: Int, lockoutUntil: Long) {
        source.setPinFailures(v, lockoutUntil)
    }

    override suspend fun setBiometrics(v: Boolean) {
        source.setBiometrics(v)
    }

    override suspend fun setLockTimeout(v: LockTimeout) {
        source.setLockTimeout(v)
    }

    override suspend fun setPermissionIntroShown() {
        source.setPermissionIntroShown()
    }

    override suspend fun setTransferGuideShown() {
        source.setTransferGuideShown()
    }

    override suspend fun setMemoHelpHidden(v: Boolean) {
        source.setMemoHelpHidden(v)
    }

    override suspend fun setSmsCaptureEnabled(v: Boolean) {
        source.setSmsCaptureEnabled(v)
    }

    override suspend fun markSmsCaptureStarted(now: Long) {
        source.markSmsCaptureStarted(now)
    }

    override suspend fun markSmsCaptureStopped(now: Long) {
        source.markSmsCaptureStopped(now)
    }

    override suspend fun setSmsLastProcessed(v: Long) {
        source.setSmsLastProcessed(v)
    }

    override suspend fun setSmsHistoryImport(v: SmsHistoryImport) {
        source.setSmsHistoryImport(v)
    }

    override suspend fun setSmsStoreFullText(v: Boolean) {
        source.setSmsStoreFullText(v)
    }

    override suspend fun setSmsCustomSenders(v: Set<String>) {
        source.setSmsCustomSenders(v)
    }

    override suspend fun setSmsSeenAt(v: Long) {
        source.setSmsSeenAt(v)
    }

    override suspend fun setFxFetchedAt(v: Long) {
        source.setFxFetchedAt(v)
    }

    override suspend fun clearSmsGap() {
        source.clearSmsGap()
    }
}
