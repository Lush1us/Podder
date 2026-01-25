package com.example.podder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(podcast: PodcastEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Transaction
    @Query("SELECT * FROM episodes ORDER BY pubDate DESC")
    fun getAllEpisodes(): Flow<List<EpisodeWithPodcast>>
}