package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object PlaybackBridge {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = mutableListOf<(MediaController) -> Unit>()

    fun connect(context: Context) {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                controller = future.get()
                val c = controller ?: return@addListener
                val work = pending.toList()
                pending.clear()
                work.forEach { it(c) }
            } catch (_: Exception) {
                pending.clear()
                controllerFuture = null
            }
        }, ContextCompatMainExecutor(context))
    }

    private fun withController(context: Context, action: (MediaController) -> Unit) {
        connect(context)
        controller?.let(action) ?: pending.add(action)
    }

    fun playUrl(context: Context, url: String, title: String? = null, artist: String? = null, artwork: String? = null) {
        withController(context) { c ->
            val item = buildItem(url, title, artist, artwork)
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun updateMetadata(context: Context, title: String?, artist: String?, artwork: String? = null) {
        withController(context) { c ->
            val current = c.currentMediaItem ?: return@withController
            val old = current.mediaMetadata
            val newTitle = title?.takeIf { it.isNotBlank() } ?: old.title?.toString() ?: "Eclipse"
            val newArtist = artist?.takeIf { it.isNotBlank() } ?: old.artist?.toString() ?: "Eclipse"
            val newArtwork = artwork?.takeIf { it.isNotBlank() } ?: old.artworkUri?.toString()
            if (newTitle == old.title?.toString() && newArtist == old.artist?.toString() &&
                newArtwork == old.artworkUri?.toString()) return@withController
            c.replaceMediaItem(
                c.currentMediaItemIndex,
                buildItem(current.localConfiguration?.uri.toString(), newTitle, newArtist, newArtwork, current.mediaId)
            )
        }
    }

    fun play(context: Context) = withController(context) { it.play() }
    fun pause(context: Context) = withController(context) { it.pause() }
    fun playPause(context: Context) = withController(context) { if (it.isPlaying) it.pause() else it.play() }
    fun next(context: Context) = withController(context) { if (it.hasNextMediaItem()) it.seekToNext() }
    fun previous(context: Context) = withController(context) { if (it.hasPreviousMediaItem()) it.seekToPrevious() }

    fun seekBy(context: Context, deltaMs: Long) = withController(context) {
        val duration = it.duration
        val target = (it.currentPosition + deltaMs).coerceAtLeast(0L)
        if (duration > 0L) it.seekTo(target.coerceAtMost(duration)) else it.seekTo(target)
    }

    fun getPositionMs(): Long = controller?.currentPosition ?: 0L
    fun getDurationMs(): Long = controller?.duration ?: 0L
    fun isPlaying(): Boolean = controller?.isPlaying == true

    private fun buildItem(url: String, title: String?, artist: String?, artwork: String?, mediaId: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title?.takeIf { it.isNotBlank() } ?: "Eclipse")
            .setArtist(artist?.takeIf { it.isNotBlank() } ?: "Eclipse")
            .apply { artwork?.takeIf { it.isNotBlank() }?.let { setArtworkUri(Uri.parse(it)) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId ?: url)
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        pending.clear()
        controller?.release()
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private class ContextCompatMainExecutor(context: Context) : java.util.concurrent.Executor {
        private val handler = Handler(context.mainLooper)
        override fun execute(command: Runnable) { handler.post(command) }
    }
}
