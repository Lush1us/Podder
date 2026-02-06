package com.example.podder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.podder.ui.AppNavigation
import com.example.podder.ui.screens.PodcastViewModel
import com.example.podder.ui.theme.PodderTheme
import com.example.podder.utils.AppLogger
import com.example.podder.utils.Originator

class MainActivity : ComponentActivity() {

    private var isFreshLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Track if this is a fresh launch (not a config change)
        isFreshLaunch = savedInstanceState == null

        // Get singletons from Application scope
        val app = application as PodderApplication

        // Factory to inject dependencies into ViewModel
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastViewModel(
                    app,
                    app.podcastRepository,
                    app.playerController,
                    app.playbackStore
                ) as T
            }
        }

        setContent {
            // Uses system dark mode setting
            PodderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val podcastViewModel: PodcastViewModel = viewModel(factory = viewModelFactory)
                    AppNavigation(viewModel = podcastViewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (isFreshLaunch) {
            AppLogger.log(application, Originator.DEVICE, "Lifecycle", "App Launched")
            isFreshLaunch = false
        } else {
            AppLogger.log(application, Originator.DEVICE, "Lifecycle", "App Resumed")
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            AppLogger.log(application, Originator.DEVICE, "Lifecycle", "App Backgrounded")
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            AppLogger.log(application, Originator.USER, "Lifecycle", "App Closed")
        }
        super.onDestroy()
    }
}
