package com.nexo.app.core.notifications

import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings

/** The settings the notification receivers read, supplied by the data layer. */
interface NotificationSettingsSource {
    suspend fun getDeviceSettings(): DeviceSettings

    suspend fun getBookSettings(): BookSettings
}
