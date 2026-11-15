package com.nexo.app.di

import com.nexo.app.core.notifications.Notifier
import com.nexo.app.system.SystemTrayNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun notifier(impl: SystemTrayNotifier): Notifier
}
