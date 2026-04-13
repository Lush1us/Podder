package dev.podder.di

import dev.podder.data.db.DatabaseDriverFactory
import dev.podder.data.network.createHttpClient
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.repository.PodcastRepositoryImpl
import dev.podder.data.discovery.DiscoveryRepository
import dev.podder.data.discovery.DiscoveryRepositoryImpl
import dev.podder.data.discovery.PodcastIndexApi
import dev.podder.data.search.SearchRepository
import dev.podder.data.search.SearchRepositoryImpl
import dev.podder.data.store.KVStore
import dev.podder.db.PodderDatabase
import dev.podder.domain.player.PlaybackStateMachine
import dev.podder.logging.PodderLogger
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun sharedModule(
    driverFactory: DatabaseDriverFactory,
    kvStorePath: String,
    podcastIndexApiKey: String = "",
    podcastIndexApiSecret: String = "",
): Module = module {
    single { driverFactory }
    single { PodderDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { createHttpClient(getOrNull<PodderLogger>()) }
    single<PodcastRepository> { PodcastRepositoryImpl(get(), get(), getOrNull<PodderLogger>()) }
    single<SearchRepository> { SearchRepositoryImpl(get()) }
    single { KVStore(kvStorePath) }
    single { PlaybackStateMachine(get(), get<PodderLogger>()) }
    single { PodcastIndexApi(get(), podcastIndexApiKey, podcastIndexApiSecret) }
    single<DiscoveryRepository> { DiscoveryRepositoryImpl(get(), get()) }
}
