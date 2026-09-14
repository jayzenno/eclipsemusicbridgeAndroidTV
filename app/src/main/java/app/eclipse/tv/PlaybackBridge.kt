package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

object PlaybackBridge {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var currentUrl: String? = null
    private var pendingUrl: String? = null

    private fun mainExecutor(context: Context): Executor = Executor { command ->
        android.os.Handler(context.mainLooper).post(command)
    }

    fun connect(context: Context) {
        if (controllerFuture != null) return
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token)
            .setListener(object : MediaController.Listener {})
            .buildAsync()
    }

    fun playUrl(
        context: Context,
        url: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artworkUri: String? = null
    ) {
        connect(context)
        if (url == currentUrl && pendingUrl == null) return
        pendingUrl = url

        controllerFuture?.addListener({
            val controller = runCatching { controllerFuture?.get() }.getOrNull() ?: return@addListener
            val target = pendingUrl ?: return@addListener
            if (target != url) return@addListener

            val metadata = MediaMetadata.Builder()
                .setTitle(title?.takeIf { it.isNotBlank() })
                .setArtist(artist?.takeIf { it.isNotBlank() })
                .setAlbumTitle(album?.takeIf { it.isNotBlank() })
                .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                .build()

            controller.setMediaItem(
                MediaItem.Builder()
                    .setUri(target)
                    .setMediaMetadata(metadata)
                    .build()
            )
            controller.prepare()
            controller.play()
            currentUrl = target
            pendingUrl = null
        }, mainExecutor(context))
    }

    fun playPause(context: Context) {
        connect(context)
        controllerFuture?.addListener({
            val controller = runCatching { controllerFuture?.get() }.getOrNull() ?: return@addListener
            if (controller.isPlaying) controller.pause() else controller.play()
        }, mainExecutor(context))
    }

    fun next(context: Context) {
        connect(context)
        controllerFuture?.addListener({
            runCatching { controllerFuture?.get()?.seekToNext() }
        }, mainExecutor(context))
    }

    fun previous(context: Context) {
        connect(context)
        controllerFuture?.addListener({
            runCatching { controllerFuture?.get()?.seekToPrevious() }
        }, mainExecutor(context))
    }

    fun stop(context: Context) {
        connect(context)
        controllerFuture?.addListener({
            runCatching { controllerFuture?.get()?.stop() }
            currentUrl = null
            pendingUrl = null
        }, mainExecutor(context))
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        pendingUrl = null
    }
}
