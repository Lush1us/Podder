package com.example.podder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.podder.data.local.PodcastEntity
import com.example.podder.data.local.EpisodeEntity

@Database(entities = [PodcastEntity::class, EpisodeEntity::class, SubscriptionEntity::class], version = 6)
abstract class PodderDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun subscriptionDao(): SubscriptionDao
}