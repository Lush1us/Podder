package dev.podder.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.podder.android.service.PodderMediaService
import dev.podder.android.ui.HomeScreen
import dev.podder.android.ui.theme.PodderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        startService(Intent(this, PodderMediaService::class.java))
        setContent {
            PodderTheme {
                HomeScreen()
            }
        }
    }
}
