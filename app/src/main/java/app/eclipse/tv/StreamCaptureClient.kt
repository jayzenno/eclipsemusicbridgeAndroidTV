package app.eclipse.tv

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class StreamCaptureClient(
    private val onAudioUrl: (String) -> Unit,
    private val onPageReady: (() -> Unit)? = null
) : WebViewClient() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUrl: String? = null
    private var pendingUrl: String? = null
    private var pendingRunnable: Runnable? = null

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        onPageReady?.invoke()
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (isAudioUrl(url)) {
            mainHandler.post { scheduleAudioUrl(url) }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun scheduleAudioUrl(url: String) {
        if (url == lastUrl || url == pendingUrl) return
        pendingUrl = url
        pendingRunnable?.let(mainHandler::removeCallbacks)
        val runnable = Runnable {
            pendingRunnable = null
            val target = pendingUrl ?: return@Runnable
            pendingUrl = null
            if (target == lastUrl) return@Runnable
            lastUrl = target
            android.util.Log.d("EclipseTV", "AUDIO_REQUEST $target")
            onAudioUrl(target)
        }
        pendingRunnable = runnable
        mainHandler.postDelayed(runnable, 180L)
    }

    fun release() {
        pendingRunnable?.let(mainHandler::removeCallbacks)
        pendingRunnable = null
        pendingUrl = null
    }

    private fun isAudioUrl(url: String): Boolean {
        val lower = url.lowercase()
        val isHttp = lower.startsWith("http://") || lower.startsWith("https://")
        if (!isHttp) return false
        return lower.contains(".m3u8") || lower.contains(".mpd") ||
            lower.contains(".mp3") || lower.contains(".m4a") ||
            lower.contains(".aac") || lower.contains(".flac") ||
            lower.contains(".ogg") || lower.contains("audio") ||
            lower.contains("stream") || lower.contains("/play")
    }
}
