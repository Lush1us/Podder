package com.example.podder.sync

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.PodderDatabase
import com.example.podder.ui.notifications.NotificationHelper

class PodcastSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "PodcastSyncWorker started")
        return try {
            val database = Room.databaseBuilder(
                applicationContext,
                PodderDatabase::class.java,
                "podder-db"
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

            val repository = PodcastRepository(
                database.podcastDao(),
                database.subscriptionDao()
            )

            val newEpisodes = repository.forceRefresh()
            Log.d(TAG, "Worker found ${newEpisodes.size} new episodes")

            // Show notification for new episodes
            NotificationHelper.createChannel(applicationContext)
            if (newEpisodes.isNotEmpty()) {
                NotificationHelper.showNewEpisodes(applicationContext, newEpisodes)
            }

            SyncScheduler.markSynced(applicationContext)
            Log.d(TAG, "PodcastSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PodcastSyncWorker failed", e)
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "Podder"
        const val WORK_NAME = "podcast_sync"
    }
}
