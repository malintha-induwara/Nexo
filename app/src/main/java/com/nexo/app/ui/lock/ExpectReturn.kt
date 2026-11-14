package com.nexo.app.ui.lock

import android.content.Context
import com.nexo.app.data.repository.AppLockRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppLockEntryPoint {
    fun appLock(): AppLockRepository
}

/** Tells the app lock the user is being sent to another app for a moment. See [AppLockRepository.expectReturn]. */
fun Context.expectReturn() {
    EntryPointAccessors.fromApplication(
        applicationContext,
        AppLockEntryPoint::class.java,
    ).appLock().expectReturn()
}
