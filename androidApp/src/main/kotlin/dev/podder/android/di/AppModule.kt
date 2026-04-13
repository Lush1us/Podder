package com.lush1us.podder.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import com.lush1us.podder.download.DownloadRepository
import com.lush1us.podder.logging.AnrWatchdog
import com.lush1us.podder.logging.JankMonitor
import com.lush1us.podder.precache.PreCacheManager
import com.lush1us.podder.queue.QueueRepository
import com.lush1us.podder.ui.queue.QueueViewModel
import dev.podder.db.PodderDatabase
import com.lush1us.podder.ui.download.DownloadViewModel
import com.lush1us.podder.ui.episode.EpisodeDetailViewModel
import com.lush1us.podder.ui.feed.FeedViewModel
import com.lush1us.podder.ui.playback.PlaybackViewModel
import com.lush1us.podder.ui.podcast.EpisodeListViewModel
import com.lush1us.podder.ui.podcast.PodcastListViewModel
import com.lush1us.podder.notification.NewEpisodeNotifier
import com.lush1us.podder.ui.discover.DiscoverViewModel
import com.lush1us.podder.ui.podcast.PodcastSettingsViewModel
import com.lush1us.podder.ui.search.SearchViewModel
import com.lush1us.podder.ui.settings.SettingsViewModel
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
