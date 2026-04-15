package com.lush1us.podder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentCallbacks2
import android.os.SystemClock
import android.content.res.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.lush1us.podder.notification.NewEpisodeNotifier
import com.lush1us.podder.di.appModule
import com.lush1us.podder.download.DownloadRepository
import dev.podder.data.store.KVStore
import com.lush1us.podder.logging.AnrWatchdog
import com.lush1us.podder.logging.AppPodderLogger
import com.lush1us.podder.logging.ContextualExceptionHandler
import com.lush1us.podder.logging.CrashContextHarvester
import com.lush1us.podder.logging.CrashUploader
import com.lush1us.podder.logging.JankMonitor
import com.lush1us.podder.worker.CacheCleanupWorker
import com.lush1us.podder.worker.RefreshWorker
import dev.podder.data.db.DatabaseDriverFactory
import dev.podder.di.sharedModule
import dev.podder.domain.player.PlaybackStateMachine
import dev.podder.logging.LogEvent
import dev.podder.logging.LogLevel
import dev.podder.logging.PodderLogger
import dev.podder.logging.Subsystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.TimeUnit

class PodderApplication : Application() {

    companion object {
        // Captured at the very start of the process — before Koin, DB, anything.
        // MainActivity uses this so the 2-second splash counts from process birth,
        // not from when the Activity starts. On warm restarts the process has been
        // alive for a long time so the splash exits immediately (no wait needed).
        val processStartMs: Long = SystemClock.elapsedRealtime()
    }


    // Created before Koin so the crash handler has a logger immediately
    private lateinit var appLogger: AppPodderLogger
    private lateinit var harvester: CrashContextHarvester

    override fun onCreate() {
        super.onCreate()

        // 1. Create logger and crash infrastructure before Koin
        val logDir = File(filesDir, "logs").also { it.mkdirs() }
        appLogger = AppPodderLogger(logDir)
        harvester = CrashContextHarvester(this, appLogger)
        ContextualExceptionHandler.install(harvester)

        // 2. Start Koin, providing logger as a pre-created singleton
        startKoin {
            androidContext(this@PodderApplication)
            modules(
                module { single<PodderLogger> { appLogger } },
                sharedModule(
                    driverFactory        = DatabaseDriverFactory(this@PodderApplication),
                    kvStorePath          = "${filesDir.absolutePath}/podder.kv",
                    podcastIndexApiKey   = BuildConfig.PI_API_KEY,
                    podcastIndexApiSecret = BuildConfig.PI_API_SECRET,
                ),
                appModule,
            )
        }

        // 3. Wire stateMachine reference into harvester post-Koin
        val stateMachine: PlaybackStateMachine by inject()
        harvester.stateMachine = stateMachine

        // 4. Log app created
        appLogger.log(LogLevel.INFO, Subsystem.APP, LogEvent.AppLifecycle.Created)

        // Coil 3 ships no default network fetcher — register the Ktor3 one so remote
        // artwork actually loads. Reuses the Ktor version already pinned for the app.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components { add(KtorNetworkFetcherFactory()) }
                .crossfade(true)
                .build()
        }

        // 5. Check for crash context from previous run and upload to dev machine
        CoroutineScope(Dispatchers.IO).launch {
            val crashContext = appLogger.readAndClearCrashContext()
            if (crashContext != null) {
                appLogger.log(LogLevel.WARN, Subsystem.APP,
                    LogEvent.AppLifecycle.PreviousCrashContextFound(
                        summary = crashContext.lines().take(5).joinToString(" | ")
                    ))
                CrashUploader.upload(applicationContext, crashContext)
            }
        }

        // 6. Cleanup expired downloads
        val downloadRepository: DownloadRepository by inject()
        CoroutineScope(Dispatchers.IO).launch {
            downloadRepository.cleanupExpiredAutoCache()
        }

        // 7. Start performance monitors
        val jankMonitor: JankMonitor by inject()
        val anrWatchdog: AnrWatchdog by inject()
        jankMonitor.start()
        anrWatchdog.start()

        // 8. Register memory pressure callbacks
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                appLogger.log(LogLevel.WARN, Subsystem.APP,
                    LogEvent.Performance.MemoryPressure(
                        availMem    = -1L,
                        totalMem    = -1L,
                        isLowMemory = level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
                        trimLevel   = level,
                    ))
            }
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() {
                appLogger.log(LogLevel.ERROR, Subsystem.APP,
                    LogEvent.AppLifecycle.MemoryWarning(
                        level     = ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
                        levelName = "LOW_MEMORY",
                    ))
            }
        })

        // 9. Create notification channel for new episode alerts
        val channel = NotificationChannel(
            NewEpisodeNotifier.CHANNEL_ID,
            "New Episodes",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)

        // 10. Schedule periodic feed refresh — WorkManager.getInstance() initialises a Room
        //     database; keep it off the main thread to avoid startup jank.
        val kvStore: KVStore by inject()
        val refreshHours = kvStore.getLong("refresh_interval_hours", 3L)
        val refreshConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            WorkManager.getInstance(this@PodderApplication).enqueueUniquePeriodicWork(
                "feed_refresh",
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<RefreshWorker>(refreshHours, TimeUnit.HOURS)
                    .setConstraints(refreshConstraints)
                    .build(),
            )
        }

        // 11. Schedule daily cache cleanup — charging + idle only, so it never competes with playback.
        val cleanupConstraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(true)
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            WorkManager.getInstance(this@PodderApplication).enqueueUniquePeriodicWork(
                "cache_cleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(cleanupConstraints)
                    .build(),
            )
        }
    }
}
