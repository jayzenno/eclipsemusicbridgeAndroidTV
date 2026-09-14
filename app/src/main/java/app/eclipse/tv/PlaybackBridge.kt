package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

object PlaybackBridge {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    fun connect(context: Context) {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
    }

    fun playUrl(context: Context, url: String) {
        connect(context)
        controllerFuture?.addListener({
            val controller = controllerFuture?.get() ?: return@addListener
            controller.setMediaItem(MediaItem.fromUri(url))
            controller.prepare()
            controller.play()
        }, ContextCompatMainExecutor(context))
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private class ContextCompatMainExecutor(context: Context) : java.util.concurrent.Executor {
        private val handler = android.os.Handler(context.mainLooper)
        override fun execute(command: Runnable) { handler.post(command) }
    }
}
