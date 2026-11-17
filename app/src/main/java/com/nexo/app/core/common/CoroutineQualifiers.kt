package com.nexo.app.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: NexoDispatchers)

enum class NexoDispatchers { Default, IO }

// For work that must outlive the screen that started it.
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
