package com.nexo.app.ui.lock

import androidx.compose.runtime.staticCompositionLocalOf

// Call before sending the user to another app they'll come straight back from (camera, file
// picker), so the app lock doesn't treat the trip as leaving.
val LocalExpectReturn = staticCompositionLocalOf<() -> Unit> { {} }
