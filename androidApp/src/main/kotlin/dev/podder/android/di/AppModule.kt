package dev.podder.android.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import dev.podder.android.download.DownloadRepository
import dev.podder.android.logging.AnrWatchdog
import dev.podder.android.logging.JankMonitor
import dev.podder.android.precache.PreCacheManager
import dev.podder.android.queue.QueueRepository
import dev.podder.android.ui.queue.QueueViewModel
import dev.podder.db.PodderDatabase
import dev.podder.android.ui.download.DownloadViewModel
import dev.podder.android.ui.episode.EpisodeDetailViewModel
import dev.podder.android.ui.feed.FeedViewModel
import dev.podder.android.ui.playback.PlaybackViewModel
import dev.podder.android.ui.podcast.EpisodeListViewModel
import dev.podder.android.ui.podcast.PodcastListViewModel
import dev.podder.android.notification.NewEpisodeNotifier
import dev.podder.android.ui.discover.DiscoverViewModel
import dev.podder.android.ui.podcast.PodcastSettingsViewModel
import dev.podder.android.ui.search.SearchViewModel
import dev.podder.android.ui.settings.SettingsViewModel
import dev.podder.logging.PodderLogger
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.Executors

val appModule = module {

    single<StandaloneDatabaseProvider> {
        StandaloneDatabaseProvider(androidContext())
    }

    single<SimpleCache> {
        val cacheDir = File(androidContext().cacheDir, "media3-cache")
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(5L * 1024 * 1024 * 1024), // 5 GB
            get<StandaloneDatabaseProvider>(),
        )
    }

    single<CacheDataSource.Factory> {
        CacheDataSource.Factory()
            .setCache(get<SimpleCache>())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    single<DownloadManager> {
        val downloadDir = File(
            androidContext().getExternalFilesDir(null) ?: androidContext().filesDir,
            "downloads"
        )
        downloadDir.mkdirs()
        DownloadManager(
            androidContext(),
            get<StandaloneDatabaseProvider>(),
            get<SimpleCache>(),
            DefaultHttpDataSource.Factory(),
            Executors.newSingleThreadExecutor(),
        )
    }

    single<DownloadRepository> {
        DownloadRepository(
            context           = androidContext(),
            downloadManager   = get(),
            database          = get<PodderDatabase>(),
            podcastRepository = get(),
            logger            = get<PodderLogger>(),
        )
    }

    single<PreCacheManager> { PreCacheManager(get(), get(), get<PodderLogger>()) }
    single<QueueRepository> { QueueRepository(get<PodderDatabase>(), get<PodderLogger>()) }

    single { JankMonitor(get<PodderLogger>()) }
    single { AnrWatchdog(get<PodderLogger>()) }

    single { NewEpisodeNotifier(androidContext(), get()) }

    viewModelOf(::PodcastListViewModel)
    viewModelOf(::QueueViewModel)
    viewModelOf(::FeedViewModel)
    viewModelOf(::PlaybackViewModel)
    viewModelOf(::DownloadViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params -> EpisodeListViewModel(params.get(), get()) }
    viewModel { params -> EpisodeDetailViewModel(params.get(), get(), get()) }
    viewModel { params -> PodcastSettingsViewModel(params.get(), get()) }
}
