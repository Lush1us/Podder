package com.example.podder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class EpisodeProgress(val guid: String, val progressInMillis: Long)

@Dao
interface PodcastDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPodcast(podcast: PodcastEntity): Long

    @Query("UPDATE podcasts SET title = :title, imageUrl = :imageUrl WHERE url = :url")
    suspend fun updatePodcast(url: String, title: String, imageUrl: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Transaction
    @Query("SELECT * FROM episodes ORDER BY pubDate DESC")
    fun getAllEpisodes(): Flow<List<EpisodeWithPodcast>>

    @Query("UPDATE episodes SET progressInMillis = :progress WHERE guid = :guid")
    suspend fun updateProgress(guid: String, progress: Long): Int

    @Query("SELECT * FROM episodes WHERE guid = :guid")
    suspend fun getEpisode(guid: String): EpisodeEntity?

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun getEpisodeCount(): Int

    @Query("SELECT guid, progressInMillis FROM episodes")
    suspend fun getAllProgress(): List<EpisodeProgress>
}