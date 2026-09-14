package app.eclipse.tv

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

object NativePlayback {
    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    fun connect(context: Context) {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        future = MediaController.Builder(context, token).buildAsync()
    }

    fun play(context: Context, url: String) {
        connect(context)
        future?.addListener({
            val controller = future?.get() ?: return@addListener
            controller.setMediaItem(MediaItem.fromUri(url))
            controller.prepare()
            controller.play()
        }, Handler(Looper.getMainLooper())::post)
    }

    fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
