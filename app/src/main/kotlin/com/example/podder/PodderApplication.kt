package com.example.podder

import android.app.Application
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.podder.data.PlaybackStore
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.PodderDatabase
import com.example.podder.data.network.PodcastSearchService
import com.example.podder.player.PlayerController
import com.example.podder.sync.SyncScheduler
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

class PodderApplication : Application() {

    lateinit var database: PodderDatabase
        private set

    lateinit var podcastRepository: PodcastRepository
        private set

    lateinit var playerController: PlayerController
        private set

    lateinit var playbackStore: PlaybackStore
        private set

    override fun onCreate() {
        super.onCreate()

        // Database (Application Scope - singleton)
        database = Room.databaseBuilder(
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

        // Repository (Application Scope - singleton)
        podcastRepository = PodcastRepository(
            database.podcastDao(),
            database.subscriptionDao(),
            applicationContext,
            searchService
        )

        // PlayerController (Application Scope - singleton)
        playerController = PlayerController(applicationContext)

        // PlaybackStore for session restoration (Application Scope - singleton)
        playbackStore = PlaybackStore(applicationContext)

        // Schedule background sync
        SyncScheduler.schedulePeriodicSync(applicationContext)
        SyncScheduler.syncIfStale(applicationContext)
    }
}
