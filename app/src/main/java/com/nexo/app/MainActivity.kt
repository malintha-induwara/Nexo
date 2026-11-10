package com.nexo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nexo.app.core.settings.DevicePrefs
import com.nexo.app.data.DataReset
import com.nexo.app.ui.AppViewModel
import com.nexo.app.ui.IntentRouter
import com.nexo.app.ui.NexoRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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
        if (savedInstanceState == null) app.setPending(IntentRouter.route(intent))
        setContent {
            NexoRoot(
                app = app,
                onEraseAll = { reset.eraseAll() },
                onBiometricsInvalidated = { prefs.setBiometrics(false) },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val keys = IntentRouter.route(intent)
        if (keys.isNotEmpty()) app.setPending(keys)
    }
}
