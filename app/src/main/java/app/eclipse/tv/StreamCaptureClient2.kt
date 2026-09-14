package app.eclipse.tv

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class StreamCaptureClient2 : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp3") || lower.contains(".m4a") || lower.contains(".aac") || lower.contains(".flac") || lower.contains(".ogg") || lower.contains("audio")) {
            Log.d("EclipseTV", "AUDIO_REQUEST $url")
        }
        return super.shouldInterceptRequest(view, request)
    }
}
