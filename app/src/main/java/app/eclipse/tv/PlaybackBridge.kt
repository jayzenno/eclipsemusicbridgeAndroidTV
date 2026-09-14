package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
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

    fun playUrl(context: Context, url: String, title: String? = null, artist: String? = null) {
        withController(context) { c ->
            val item = buildItem(url, title, artist)
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun updateMetadata(context: Context, title: String?, artist: String?) {
        withController(context) { c ->
            val current = c.currentMediaItem ?: return@withController
            c.replaceMediaItem(c.currentMediaItemIndex, buildItem(current.localConfiguration?.uri.toString(), title, artist, current.mediaId))
        }
    }

    fun play(context: Context) = withController(context) { it.play() }
    fun pause(context: Context) = withController(context) { it.pause() }
    fun playPause(context: Context) = withController(context) { if (it.isPlaying) it.pause() else it.play() }
    fun next(context: Context) = withController(context) { if (it.hasNextMediaItem) it.seekToNext() }
    fun previous(context: Context) = withController(context) { if (it.hasPreviousMediaItem) it.seekToPrevious() }

    private fun buildItem(url: String, title: String?, artist: String?, mediaId: String? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title?.takeIf { it.isNotBlank() } ?: "Eclipse")
            .setArtist(artist?.takeIf { it.isNotBlank() } ?: "Eclipse")
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
