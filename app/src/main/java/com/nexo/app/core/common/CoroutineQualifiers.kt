package com.nexo.app.core.common

import javax.inject.Qualifier

/** Which dispatcher an injected `CoroutineDispatcher` runs on. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: NexoDispatchers)

enum class NexoDispatchers { Default, IO }

/** Scope for work that must outlive the screen that started it; lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
