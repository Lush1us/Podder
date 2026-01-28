package com.example.podder.sync

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.podder.data.PodcastRepository
import com.example.podder.data.local.PodderDatabase

class PodcastSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
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

            repository.forceRefresh()
            SyncScheduler.markSynced(applicationContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "podcast_sync"
    }
}
