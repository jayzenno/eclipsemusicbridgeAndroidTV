package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object PlaybackBridge {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var currentUrl: String? = null
    private var pendingUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun connect(context: Context) {
        if (controllerFuture != null) return
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
        }, mainHandler::post)
    }

    private fun withController(context: Context, action: (MediaController) -> Unit) {
        connect(context)
        controller?.let(action) ?: controllerFuture?.addListener({
            val resolved = controller ?: runCatching { controllerFuture?.get() }.getOrNull()
            if (resolved != null) {
                controller = resolved
                action(resolved)
            }
        }, mainHandler::post)
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

        withController(context) { mediaController ->
            val target = pendingUrl ?: return@withController
            if (target != url) return@withController

            val metadata = MediaMetadata.Builder()
                .setTitle(title?.takeIf { it.isNotBlank() })
                .setArtist(artist?.takeIf { it.isNotBlank() })
                .setAlbumTitle(album?.takeIf { it.isNotBlank() })
                .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                .build()

            mediaController.setMediaItem(
                MediaItem.Builder()
                    .setUri(target)
                    .setMediaMetadata(metadata)
                    .build()
            )
            mediaController.prepare()
            mediaController.play()
            currentUrl = target
            pendingUrl = null
        }
    }

    fun updateMetadata(
        context: Context,
        title: String?,
        artist: String?,
        album: String?,
        artworkUri: String?
    ) {
        withController(context) { mediaController ->
            val index = mediaController.currentMediaItemIndex
            if (index < 0 || index >= mediaController.mediaItemCount) return@withController
            val oldItem = mediaController.getMediaItemAt(index)
            val metadata = MediaMetadata.Builder()
                .setTitle(title?.takeIf { it.isNotBlank() })
                .setArtist(artist?.takeIf { it.isNotBlank() })
                .setAlbumTitle(album?.takeIf { it.isNotBlank() })
                .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse))
                .build()
            val position = mediaController.currentPosition
            mediaController.replaceMediaItem(index, oldItem.buildUpon().setMediaMetadata(metadata).build())
            mediaController.seekTo(index, position)
        }
    }

    fun playPause(context: Context) {
        withController(context) { mediaController ->
            if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
        }
    }

    fun next(context: Context) {
        withController(context) { mediaController ->
            if (mediaController.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT)) {
                mediaController.seekToNext()
            }
        }
    }

    fun previous(context: Context) {
        withController(context) { mediaController ->
            if (mediaController.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS)) {
                mediaController.seekToPrevious()
            }
        }
    }

    fun stop(context: Context) {
        withController(context) { mediaController ->
            mediaController.stop()
            currentUrl = null
            pendingUrl = null
        }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        pendingUrl = null
        currentUrl = null
    }
}
