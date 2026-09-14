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

    fun connect(context: Context) {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({ controller = controllerFuture?.get() }, ContextCompatMainExecutor(context))
    }

    fun playUrl(context: Context, url: String, title: String? = null, artist: String? = null) {
        connect(context)
        controllerFuture?.addListener({
            val c = controller ?: controllerFuture?.get() ?: return@addListener
            val metadata = MediaMetadata.Builder()
                .setTitle(title?.takeIf { it.isNotBlank() } ?: "Eclipse")
                .setArtist(artist?.takeIf { it.isNotBlank() } ?: "Eclipse")
                .build()
            val item = MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build()
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }, ContextCompatMainExecutor(context))
    }

    fun playPause(context: Context) { connect(context); controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next(context: Context) { connect(context); controller?.seekToNext() }
    fun previous(context: Context) { connect(context); controller?.seekToPrevious() }

    fun release() {
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
