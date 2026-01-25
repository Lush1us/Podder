package com.example.podder.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class PlayerController(private val context: Context) {
    private var controller: MediaController? = null

    private suspend fun ensureController(): MediaController = withContext(Dispatchers.Main) {
        controller ?: run {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PlaybackService::class.java)
            )
            MediaController.Builder(context, sessionToken)
                .buildAsync()
                .await()
                .also { controller = it }
        }
    }

    suspend fun play(url: String) = withContext(Dispatchers.Main) {
        try {
            val mediaController = ensureController()
            // Create item directly from URI
            val mediaItem = MediaItem.fromUri(Uri.parse(url))

            mediaController.setMediaItem(mediaItem)
            mediaController.prepare()
            mediaController.play()
            Log.d("PlayerController", "Playing: $url")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
