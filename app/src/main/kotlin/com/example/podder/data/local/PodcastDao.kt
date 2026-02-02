package com.example.podder.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class EpisodeProgress(val guid: String, val progressInMillis: Long, val finishedAt: Long?)
data class EpisodeDownload(val guid: String, val localFilePath: String?)

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

    @Query("SELECT guid, progressInMillis, finishedAt FROM episodes")
    suspend fun getAllProgress(): List<EpisodeProgress>

    @Query("SELECT guid, localFilePath FROM episodes WHERE localFilePath IS NOT NULL")
    suspend fun getAllDownloads(): List<EpisodeDownload>

    @Transaction
    @Query("SELECT * FROM episodes WHERE podcastUrl = :podcastUrl ORDER BY pubDate DESC")
    fun getEpisodesByPodcast(podcastUrl: String): Flow<List<EpisodeWithPodcast>>

    @Query("SELECT * FROM podcasts WHERE url = :url")
    suspend fun getPodcast(url: String): PodcastEntity?

    @Query("UPDATE episodes SET localFilePath = :path WHERE guid = :guid")
    suspend fun updateLocalFile(guid: String, path: String): Int

    @Query("UPDATE episodes SET finishedAt = :finishedAt WHERE guid = :guid")
    suspend fun updateFinishedAt(guid: String, finishedAt: Long): Int

    @Query("UPDATE episodes SET localFilePath = NULL WHERE guid = :guid")
    suspend fun clearLocalFile(guid: String): Int

    @Query("SELECT * FROM episodes WHERE localFilePath IS NOT NULL AND finishedAt IS NOT NULL AND finishedAt < :cutoffTime")
    suspend fun getExpiredDownloads(cutoffTime: Long): List<EpisodeEntity>

    @Query("SELECT p.* FROM podcasts p INNER JOIN subscriptions s ON p.url = s.url ORDER BY p.title")
    fun getSubscribedPodcasts(): Flow<List<PodcastEntity>>
}