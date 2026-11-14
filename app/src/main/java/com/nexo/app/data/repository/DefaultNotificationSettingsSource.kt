package com.nexo.app.data.repository

import com.nexo.app.core.model.BookSettings
import com.nexo.app.core.model.DeviceSettings
import com.nexo.app.core.notifications.NotificationSettingsSource
import javax.inject.Inject

class DefaultNotificationSettingsSource @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val refs: ReferenceDataRepository,
) : NotificationSettingsSource {
    override suspend fun getDeviceSettings(): DeviceSettings = prefs.getDeviceSettings()

    override suspend fun getBookSettings(): BookSettings = refs.getRefData().settings
}
