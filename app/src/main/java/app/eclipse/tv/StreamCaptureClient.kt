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
        if (request.method.equals("GET", ignoreCase = true) && isAudioUrl(url)) {
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
        // Short debounce keeps rapid duplicate segment requests from restarting
        // playback while adding almost no perceived delay to a real track change.
        mainHandler.postDelayed(runnable, 60L)
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

        val path = lower.substringBefore('?').substringBefore('#')
        return path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            path.endsWith(".mp3") || path.endsWith(".m4a") ||
            path.endsWith(".aac") || path.endsWith(".flac") ||
            path.endsWith(".ogg") || path.contains("/audio/") ||
            path.contains("/stream/") || path.endsWith("/play")
    }
}
