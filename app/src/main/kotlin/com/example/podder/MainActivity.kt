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
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.PodderDatabase
import com.example.podder.data.network.PodcastSearchService
import com.example.podder.player.PlayerController
import com.example.podder.ui.AppNavigation
import com.example.podder.ui.screens.PodcastViewModel
import com.example.podder.ui.theme.PodderTheme
import com.example.podder.sync.SyncScheduler
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Database & Repo (Application Scope)
        val database = Room.databaseBuilder(
            applicationContext,
            PodderDatabase::class.java,
            "podder-db"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()

        // iTunes Search API for podcast discovery
        // Note: iTunes returns text/javascript content type, not application/json
        val json = Json { ignoreUnknownKeys = true }
        val searchRetrofit = Retrofit.Builder()
            .baseUrl("https://itunes.apple.com/")
            .addConverterFactory(json.asConverterFactory("text/javascript".toMediaType()))
            .build()
        val searchService = searchRetrofit.create(PodcastSearchService::class.java)

        val podcastRepository = PodcastRepository(
            database.podcastDao(),
            database.subscriptionDao(),
            applicationContext,
            searchService
        )

        // PlayerController (Application Scope)
        val playerController = PlayerController(applicationContext)

        // Factory to inject dependencies into ViewModel
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PodcastViewModel(podcastRepository, playerController) as T
            }
        }

        // Schedule background sync
        SyncScheduler.schedulePeriodicSync(applicationContext)
        SyncScheduler.syncIfStale(applicationContext)

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
}
