package com.nexo.app.ui.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.nexo.app.core.common.SystemZoneClock
import java.time.Clock

/** Where composables read "now" and "today". Provided by the app; previews and tests may fix it. */
val LocalClock = staticCompositionLocalOf<Clock> { SystemZoneClock }
