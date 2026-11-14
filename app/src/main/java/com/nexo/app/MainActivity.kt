package com.nexo.app

import android.app.UiModeManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.core.settings.ThemeMode
import com.nexo.app.data.DataReset
import com.nexo.app.ui.AppViewModel
import com.nexo.app.ui.IntentRouter
import com.nexo.app.ui.NexoRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var reset: DataReset
    @Inject lateinit var prefs: DevicePrefs

    private val app: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { app.device.value == null }
        lifecycleScope.launch {
            app.device.mapNotNull { it?.themeMode }.distinctUntilChanged().collect(::syncSystemNightMode)
        }
        if (savedInstanceState == null) app.setPending(IntentRouter.route(intent))
        setContent {
            NexoRoot(
                app = app,
                onEraseAll = { reset.eraseAll() },
                onBiometricsInvalidated = { prefs.setBiometrics(false) },
            )
        }
    }

    /**
     * The splash screen is drawn by the system before the app runs, so it only sees the phone's
     * dark-mode setting. On Android 12+ the in-app choice is handed to the system, which keeps it
     * and uses it for the next splash (and resolves `-night` resources to match).
     */
    private fun syncSystemNightMode(mode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (mode) {
                ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val keys = IntentRouter.route(intent)
        if (keys.isNotEmpty()) app.setPending(keys)
    }
}
