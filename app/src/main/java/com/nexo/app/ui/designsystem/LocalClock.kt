package com.nexo.app.ui.designsystem

import androidx.compose.runtime.staticCompositionLocalOf
import com.nexo.app.core.common.SystemZoneClock
import java.time.Clock

// "Now" and "today" for composables; previews and tests may fix it.
val LocalClock = staticCompositionLocalOf<Clock> { SystemZoneClock }
