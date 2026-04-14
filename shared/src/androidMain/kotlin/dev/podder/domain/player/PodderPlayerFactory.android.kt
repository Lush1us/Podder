package dev.podder.domain.player

import android.content.Context

actual class PodderPlayerFactory(private val context: Context) {
    actual fun create(): PodderPlayer = AndroidPodderPlayer(context.applicationContext)
}
