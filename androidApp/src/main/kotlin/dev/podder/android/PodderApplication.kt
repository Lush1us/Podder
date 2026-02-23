package dev.podder.android

import android.app.Application
import dev.podder.data.db.DatabaseDriverFactory
import dev.podder.android.di.appModule
import dev.podder.di.sharedModule
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
    }
}
