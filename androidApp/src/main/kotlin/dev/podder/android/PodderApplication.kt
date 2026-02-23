package dev.podder.android

import android.app.Application
import dev.podder.android.di.appModule
import dev.podder.android.download.DownloadRepository
import dev.podder.data.db.DatabaseDriverFactory
import dev.podder.di.sharedModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PodderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PodderApplication)
            modules(
                sharedModule(
                    driverFactory = DatabaseDriverFactory(this@PodderApplication),
                    kvStorePath   = "${filesDir.absolutePath}/podder.kv",
                ),
                appModule,
            )
        }
        val downloadRepository: DownloadRepository by inject()
        CoroutineScope(Dispatchers.IO).launch {
            downloadRepository.cleanupExpiredAutoCache()
        }
    }
}
