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
import com.example.podder.utils.AppLogger
import com.example.podder.utils.Originator
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

        // Set up crash handler before anything else
        setupCrashHandler()

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

        // PlaybackStore for session restoration (Application Scope - singleton)
        playbackStore = PlaybackStore(applicationContext)

        // PlayerController (Application Scope - singleton) - receives PlaybackStore for event-triggered saves
        playerController = PlayerController(applicationContext, playbackStore)

        // Schedule background sync
        SyncScheduler.schedulePeriodicSync(applicationContext)
        SyncScheduler.syncIfStale(applicationContext)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Log the crash synchronously (blocking) since we're about to die
            try {
                val logFile = java.io.File(filesDir, "podder_events.log")
                val timestamp = java.text.SimpleDateFormat("MM/dd HH:mm:ss.SSS", java.util.Locale.US)
                    .format(java.util.Date())
                val crashLog = "$timestamp [DEVICE][   Lifecycle    ][    App Crashed     ] ${throwable.javaClass.simpleName}: ${throwable.message}\n"
                logFile.appendText(crashLog)
            } catch (_: Exception) {
                // Can't log, just proceed to default handler
            }

            // Pass to default handler to show crash dialog / terminate
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
