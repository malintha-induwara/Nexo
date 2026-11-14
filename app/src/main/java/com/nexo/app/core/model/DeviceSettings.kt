package com.nexo.app.core.model

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class ThemeColor { WHITE, RED, PINK, GREEN, BLUE, BLACK }

/** First-run "Add past transactions" offer. */
enum class SmsHistoryImport { NOT_OFFERED, OFFERED, DONE, SKIPPED }

enum class LockTimeout(val seconds: Int) {
    IMMEDIATELY(0),
    ONE_MINUTE(60),
    FIVE_MINUTES(300),
    FIFTEEN_MINUTES(900),
    ONE_HOUR(3600),
}

/** Device-level preferences. Never part of a data backup. */
data class DeviceSettings(
    val setupDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themeColor: ThemeColor = ThemeColor.WHITE,
    val numberPadDescending: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val reminderDays: Set<Int> = (1..7).toSet(),
    val copyWithToday: Boolean? = null,
    val groupedAccountPicker: Boolean = false,
    val photoNoticeShown: Boolean = false,
    val whatsNewVersion: Int = 0,
    val collapsedGroups: Set<String> = emptySet(),
    val lastAutoBackup: Long = 0,
    val repeatDay: String = "",
    val pinHash: String? = null,
    val pinFailures: Int = 0,
    val pinLockoutUntil: Long = 0,
    val biometricsEnabled: Boolean = false,
    val lockTimeout: LockTimeout = LockTimeout.IMMEDIATELY,
    val permissionIntroShown: Boolean = false,
    val transferGuideShown: Boolean = false,
    val budgetHelpHidden: Boolean = false,
    val memoHelpHidden: Boolean = false,
    /** "Auto-capture bank SMS", on by default. */
    val smsCaptureEnabled: Boolean = true,
    /** Start point: when capture last became active (switch on and permission granted); 0 = not active. */
    val smsCaptureStartedAt: Long = 0,
    /** When capture last stopped; turns into the re-enable gap. */
    val smsCaptureStoppedAt: Long = 0,
    /** Catch-up watermark (inbox `date`); never earlier than the start point. */
    val smsLastProcessedAt: Long = 0,
    val smsHistoryImport: SmsHistoryImport = SmsHistoryImport.NOT_OFFERED,
    /** Keep message text unredacted. */
    val smsStoreFullText: Boolean = false,
    val smsCustomSenders: Set<String> = emptySet(),
    /** Last time the Review Queue was opened; the notification counts items captured after it. */
    val smsSeenAt: Long = 0,
    /** Re-enable hint: messages in [smsGapFrom, smsGapUntil) were not captured. 0 = no hint. */
    val smsGapFrom: Long = 0,
    val smsGapUntil: Long = 0,
    /** Last successful AllRatesToday fetch (at most every 6 hours). */
    val fxFetchedAt: Long = 0,
) {
    val lockEnabled: Boolean get() = pinHash != null
}
