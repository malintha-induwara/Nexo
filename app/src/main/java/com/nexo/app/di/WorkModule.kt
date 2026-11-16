package com.nexo.app.di

import com.nexo.app.core.work.BackgroundWork
import com.nexo.app.data.work.WorkManagerBackgroundWork
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds
    abstract fun backgroundWork(impl: WorkManagerBackgroundWork): BackgroundWork
}
