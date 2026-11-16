package com.nexo.app.ui.lock

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Call just before the app sends the user to another app and expects them straight back: the
 * camera for a receipt, a file picker. The app lock does not treat that trip as leaving the app.
 */
val LocalExpectReturn = staticCompositionLocalOf<() -> Unit> { {} }
