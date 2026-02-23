package dev.podder.di

import dev.podder.data.db.DatabaseDriverFactory
import dev.podder.data.network.createHttpClient
import dev.podder.data.repository.PodcastRepository
import dev.podder.data.repository.PodcastRepositoryImpl
import dev.podder.data.store.KVStore
import dev.podder.db.PodderDatabase
import dev.podder.domain.player.PlaybackStateMachine
import org.koin.core.module.Module
import org.koin.dsl.module

fun sharedModule(driverFactory: DatabaseDriverFactory, kvStorePath: String): Module = module {
    single { driverFactory }
    single { PodderDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { createHttpClient() }
    single<PodcastRepository> { PodcastRepositoryImpl(get(), get()) }
    single { KVStore(kvStorePath) }
    single { PlaybackStateMachine(get()) }
}
