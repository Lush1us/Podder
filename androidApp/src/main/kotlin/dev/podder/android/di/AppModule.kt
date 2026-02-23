package dev.podder.android.di

import dev.podder.android.ui.episode.EpisodeDetailViewModel
import dev.podder.android.ui.feed.FeedViewModel
import dev.podder.android.ui.playback.PlaybackViewModel
import dev.podder.android.ui.podcast.EpisodeListViewModel
import dev.podder.android.ui.podcast.PodcastListViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::PodcastListViewModel)
    viewModelOf(::FeedViewModel)
    viewModelOf(::PlaybackViewModel)
    viewModel { params -> EpisodeListViewModel(params.get(), get()) }
    viewModel { params -> EpisodeDetailViewModel(params.get(), get(), get()) }
}
