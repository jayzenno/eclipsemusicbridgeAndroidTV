package app.eclipse.tv

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class StreamCaptureClient(private val context: Context) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
        val url = request.url.toString()
        val lower = url.lowercase()
        val looksLikeAudio = lower.contains(".m3u8") || lower.contains(".mpd") ||
            lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".aac") ||
            lower.contains(".flac") || lower.contains(".ogg") || lower.contains("audio")
        if (looksLikeAudio) {
            android.util.Log.d("EclipseTV", "Audio request: $url")
        }
        return super.shouldInterceptRequest(view, request)
    }
}
