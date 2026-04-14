package com.lush1us.podder.media

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lush1us.podder.service.PodderMediaService
import com.google.common.util.concurrent.ListenableFuture

/**
 * Owns a single [MediaController] bound to [PodderMediaService] via its [SessionToken].
 *
 * Calling [connect] forces the OS to wake and bind the service, resolving the lifecycle desync
 * where the service could be killed while the UI remained alive. Calling [release] unbinds.
 *
 * [controller] is null until the async connection completes, or after [release] is called.
 * Callers that need to wait for a live controller should poll with a coroutine + [delay].
 */
class MediaControllerManager(private val context: Context) {

    private val sessionToken = SessionToken(
        context,
        ComponentName(context, PodderMediaService::class.java),
    )

    private var controllerFuture: ListenableFuture<MediaController>? = null

    @Volatile
    private var _controller: MediaController? = null

    /** The connected controller, or null if not yet connected or already released. */
    val controller: MediaController?
        get() = _controller?.takeIf { it.isConnected }

    /**
     * Begin connecting to [PodderMediaService]. The OS binds (and starts, if needed) the service
     * to satisfy this request. Idempotent — safe to call on every [onStart].
     */
    fun connect() {
        if (_controller?.isConnected == true) return
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                _controller = runCatching { future.get() }.getOrNull()
            },
            context.mainExecutor,
        )
    }

    /**
     * Release the controller and unbind from the service.
     * Call this from [onStop] so the binding is dropped when the UI leaves the foreground.
     */
    fun release() {
        val future = controllerFuture ?: return
        MediaController.releaseFuture(future)
        _controller = null
        controllerFuture = null
    }
}
