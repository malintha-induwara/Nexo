package com.nexo.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.model.LockTimeout
import com.nexo.app.core.model.SmsHistoryImport
import com.nexo.app.core.model.ThemeColor
import com.nexo.app.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nexo_device")

/** Device-only preferences in the `nexo_device` DataStore. Never part of a data backup. */
@Singleton
class DevicePreferencesDataSource @Inject constructor(@ApplicationContext private val context: Context) {
    private object K {
        val setupDone = booleanPreferencesKey("setup_done")
        val themeMode = stringPreferencesKey("theme_mode")
        val themeColor = stringPreferencesKey("theme_color")
        val numberPadDescending = booleanPreferencesKey("number_pad_descending")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val reminderDays = stringSetPreferencesKey("reminder_days")
        val copyWithToday = stringPreferencesKey("copy_with_today")
        val groupedPicker = booleanPreferencesKey("grouped_account_picker")
        val photoNotice = booleanPreferencesKey("photo_notice_shown")
        val whatsNew = intPreferencesKey("whats_new_version")
        val collapsed = stringSetPreferencesKey("collapsed_groups")
        val lastAutoBackup = longPreferencesKey("last_auto_backup")
        val repeatDay = stringPreferencesKey("repeat_day")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockoutUntil = longPreferencesKey("pin_lockout_until")
        val biometrics = booleanPreferencesKey("biometrics_enabled")
        val lockTimeout = stringPreferencesKey("lock_timeout")
        val permissionIntro = booleanPreferencesKey("permission_intro_shown")
        val transferGuide = booleanPreferencesKey("transfer_guide_shown")
        val budgetHelpHidden = booleanPreferencesKey("budget_help_hidden")
        val memoHelpHidden = booleanPreferencesKey("memo_help_hidden")
        val smsCaptureEnabled = booleanPreferencesKey("sms_capture_enabled")
        val smsCaptureStartedAt = longPreferencesKey("sms_capture_started_at")
        val smsCaptureStoppedAt = longPreferencesKey("sms_capture_stopped_at")
        val smsLastProcessedAt = longPreferencesKey("sms_last_processed_at")
        val smsHistoryImport = stringPreferencesKey("sms_history_import_state")
        val smsStoreFullText = booleanPreferencesKey("sms_store_full_text")
        val smsCustomSenders = stringSetPreferencesKey("sms_custom_senders")
        val smsSeenAt = longPreferencesKey("sms_seen_at")
        val smsGapFrom = longPreferencesKey("sms_gap_from")
        val smsGapUntil = longPreferencesKey("sms_gap_until")
        val fxFetchedAt = longPreferencesKey("fx_fetched_at")
    }


    val deviceSettings: Flow<DeviceSettings> = context.dataStore.data.map { p ->
        DeviceSettings(
            setupDone = p[K.setupDone] ?: false,
            themeMode = p[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            themeColor = p[K.themeColor]?.let { runCatching { ThemeColor.valueOf(it) }.getOrNull() } ?: ThemeColor.WHITE,
            numberPadDescending = p[K.numberPadDescending] ?: false,
            reminderEnabled = p[K.reminderEnabled] ?: false,
            reminderHour = p[K.reminderHour] ?: 21,
            reminderMinute = p[K.reminderMinute] ?: 0,
            reminderDays = p[K.reminderDays]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: (1..7).toSet(),
            copyWithToday = p[K.copyWithToday]?.toBooleanStrictOrNull(),
            groupedAccountPicker = p[K.groupedPicker] ?: false,
            photoNoticeShown = p[K.photoNotice] ?: false,
            whatsNewVersion = p[K.whatsNew] ?: 0,
            collapsedGroups = p[K.collapsed] ?: emptySet(),
            lastAutoBackup = p[K.lastAutoBackup] ?: 0,
            repeatDay = p[K.repeatDay] ?: "",
            pinHash = p[K.pinHash],
            pinFailures = p[K.pinFailures] ?: 0,
            pinLockoutUntil = p[K.pinLockoutUntil] ?: 0,
            biometricsEnabled = p[K.biometrics] ?: false,
            lockTimeout = p[K.lockTimeout]?.let { runCatching { LockTimeout.valueOf(it) }.getOrNull() } ?: LockTimeout.IMMEDIATELY,
            permissionIntroShown = p[K.permissionIntro] ?: false,
            transferGuideShown = p[K.transferGuide] ?: false,
            budgetHelpHidden = p[K.budgetHelpHidden] ?: false,
            memoHelpHidden = p[K.memoHelpHidden] ?: false,
            smsCaptureEnabled = p[K.smsCaptureEnabled] ?: true,
            smsCaptureStartedAt = p[K.smsCaptureStartedAt] ?: 0,
            smsCaptureStoppedAt = p[K.smsCaptureStoppedAt] ?: 0,
            smsLastProcessedAt = p[K.smsLastProcessedAt] ?: 0,
            smsHistoryImport = p[K.smsHistoryImport]?.let { runCatching { SmsHistoryImport.valueOf(it) }.getOrNull() } ?: SmsHistoryImport.NOT_OFFERED,
            smsStoreFullText = p[K.smsStoreFullText] ?: false,
            smsCustomSenders = p[K.smsCustomSenders] ?: emptySet(),
            smsSeenAt = p[K.smsSeenAt] ?: 0,
            smsGapFrom = p[K.smsGapFrom] ?: 0,
            smsGapUntil = p[K.smsGapUntil] ?: 0,
            fxFetchedAt = p[K.fxFetchedAt] ?: 0,
        )
    }

    suspend fun current(): DeviceSettings = deviceSettings.first()


    suspend fun setSetupDone(v: Boolean) = context.dataStore.edit { it[K.setupDone] = v }
    suspend fun setThemeMode(v: ThemeMode) = context.dataStore.edit { it[K.themeMode] = v.name }
    suspend fun setNumberPadDescending(v: Boolean) = context.dataStore.edit { it[K.numberPadDescending] = v }
    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int, days: Set<Int>) = context.dataStore.edit {
        it[K.reminderEnabled] = enabled
        it[K.reminderHour] = hour
        it[K.reminderMinute] = minute
        it[K.reminderDays] = days.map(Int::toString).toSet()
    }
    suspend fun setCopyWithToday(v: Boolean) = context.dataStore.edit { it[K.copyWithToday] = v.toString() }
    suspend fun setGroupedPicker(v: Boolean) = context.dataStore.edit { it[K.groupedPicker] = v }
    suspend fun setPhotoNoticeShown() = context.dataStore.edit { it[K.photoNotice] = true }
    suspend fun setWhatsNewVersion(v: Int) = context.dataStore.edit { it[K.whatsNew] = v }
    suspend fun setCollapsedGroups(v: Set<String>) = context.dataStore.edit { it[K.collapsed] = v }
    suspend fun setLastAutoBackup(v: Long) = context.dataStore.edit { it[K.lastAutoBackup] = v }
    suspend fun setRepeatDay(v: String) = context.dataStore.edit { it[K.repeatDay] = v }
    suspend fun setPin(hash: String?) = context.dataStore.edit {
        if (hash == null) it.remove(K.pinHash) else it[K.pinHash] = hash
        it[K.pinFailures] = 0
        it[K.pinLockoutUntil] = 0
        if (hash == null) it[K.biometrics] = false
    }
    suspend fun setPinFailures(v: Int, lockoutUntil: Long = 0) = context.dataStore.edit {
        it[K.pinFailures] = v
        it[K.pinLockoutUntil] = lockoutUntil
    }
    suspend fun setBiometrics(v: Boolean) = context.dataStore.edit { it[K.biometrics] = v }
    suspend fun setLockTimeout(v: LockTimeout) = context.dataStore.edit { it[K.lockTimeout] = v.name }
    suspend fun setPermissionIntroShown() = context.dataStore.edit { it[K.permissionIntro] = true }
    suspend fun setTransferGuideShown() = context.dataStore.edit { it[K.transferGuide] = true }
    suspend fun setMemoHelpHidden(v: Boolean) = context.dataStore.edit { it[K.memoHelpHidden] = v }

    suspend fun setSmsCaptureEnabled(v: Boolean) = context.dataStore.edit { it[K.smsCaptureEnabled] = v }

    /** Capture became active: a new start point. After an earlier stop, the gap becomes the re-enable hint. */
    suspend fun markSmsCaptureStarted(now: Long) = context.dataStore.edit {
        val stopped = it[K.smsCaptureStoppedAt] ?: 0
        if (stopped in 1 until now) {
            it[K.smsGapFrom] = stopped
            it[K.smsGapUntil] = now
        }
        it[K.smsCaptureStartedAt] = now
        it[K.smsLastProcessedAt] = now
        it[K.smsCaptureStoppedAt] = 0
    }

    suspend fun markSmsCaptureStopped(now: Long) = context.dataStore.edit {
        it[K.smsCaptureStartedAt] = 0
        it[K.smsCaptureStoppedAt] = now
    }

    suspend fun setSmsLastProcessed(v: Long) = context.dataStore.edit { it[K.smsLastProcessedAt] = v }
    suspend fun setSmsHistoryImport(v: SmsHistoryImport) = context.dataStore.edit { it[K.smsHistoryImport] = v.name }
    suspend fun setSmsStoreFullText(v: Boolean) = context.dataStore.edit { it[K.smsStoreFullText] = v }
    suspend fun setSmsCustomSenders(v: Set<String>) = context.dataStore.edit { it[K.smsCustomSenders] = v }
    suspend fun setSmsSeenAt(v: Long) = context.dataStore.edit { it[K.smsSeenAt] = v }
    suspend fun setFxFetchedAt(v: Long) = context.dataStore.edit { it[K.fxFetchedAt] = v }
    suspend fun clearSmsGap() = context.dataStore.edit {
        it[K.smsGapFrom] = 0
        it[K.smsGapUntil] = 0
    }
}
