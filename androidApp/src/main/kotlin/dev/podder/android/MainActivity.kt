package com.lush1us.podder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lush1us.podder.service.PodderMediaService
import com.lush1us.podder.ui.HomeScreen
import com.lush1us.podder.ui.theme.PodderTheme
import dev.podder.data.store.KVStore
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val kvStore: KVStore by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        var themeMode by mutableStateOf(kvStore.getString("theme_mode", "system"))

        // Count 2 seconds from when the process was born (Application.onCreate),
        // not from here — so Koin, DB, and ART warmup eat into the 2 seconds
        // rather than adding to them. On warm restarts the process has been alive
        // for ages so this exits immediately and the app just appears.
        splashScreen.setKeepOnScreenCondition {
            android.os.SystemClock.elapsedRealtime() - PodderApplication.processStartMs < 2_000L
        }
        splashScreen.setOnExitAnimationListener { it.remove() }

        startService(Intent(this, PodderMediaService::class.java))
        setContent {
            PodderTheme(themeMode = themeMode) {
                HomeScreen(
                    themeMode     = themeMode,
                    onThemeChange = { mode ->
                        themeMode = mode
                        kvStore.putString("theme_mode", mode)
                    },
                )
            }
        }
    }
}
