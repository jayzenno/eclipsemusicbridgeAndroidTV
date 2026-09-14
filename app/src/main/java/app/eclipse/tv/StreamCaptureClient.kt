package app.eclipse.tv

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class StreamCaptureClient(
    private val onAudioUrl: (String) -> Unit
) : WebViewClient() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastUrl: String? = null

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        if (isAudioUrl(url)) {
            android.util.Log.d("EclipseTV", "AUDIO_REQUEST $url")
            mainHandler.post {
                if (url != lastUrl) {
                    lastUrl = url
                    onAudioUrl(url)
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isAudioUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") && (
            lower.contains(".m3u8") || lower.contains(".mpd") ||
                lower.contains(".mp3") || lower.contains(".m4a") ||
                lower.contains(".aac") || lower.contains(".flac") ||
                lower.contains(".ogg") || lower.contains("audio") ||
                lower.contains("stream") || lower.contains("/play")
            )
    }
}
