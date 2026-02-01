package com.example.podder.sync

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.podder.data.local.PodderDatabase
import com.example.podder.ui.notifications.NotificationHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class DownloadEpisodeWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val guid = inputData.getString(KEY_GUID) ?: return Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL) ?: return Result.failure()
        val episodeTitle = inputData.getString(KEY_EPISODE_TITLE) ?: "Episode"

        Log.d(TAG, "DownloadEpisodeWorker started for: $episodeTitle")

        NotificationHelper.createChannel(applicationContext)
        NotificationHelper.showDownloadStarted(applicationContext, episodeTitle)

        return try {
            val database = Room.databaseBuilder(
                applicationContext,
                PodderDatabase::class.java,
                "podder-db"
            )
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

            val downloadsDir = File(applicationContext.filesDir, "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Use SHA-256 hash of GUID to guarantee unique filenames (no collisions)
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(guid.toByteArray())
                .take(16)
                .joinToString("") { "%02x".format(it) }
            val safeFileName = "$hash.mp3"
            val outputFile = File(downloadsDir, safeFileName)

            downloadFile(audioUrl, outputFile)

            database.podcastDao().updateLocalFile(guid, outputFile.absolutePath)

            Log.d(TAG, "Download complete: ${outputFile.absolutePath}")
            NotificationHelper.showDownloadComplete(applicationContext, episodeTitle)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for: $episodeTitle", e)
            NotificationHelper.cancelDownloadNotification(applicationContext)
            Result.failure()
        }
    }

    private fun downloadFile(urlString: String, outputFile: File) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "Podder"
        const val KEY_GUID = "guid"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_EPISODE_TITLE = "episode_title"
    }
}
