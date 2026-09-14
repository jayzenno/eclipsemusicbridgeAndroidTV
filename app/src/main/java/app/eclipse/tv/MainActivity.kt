package app.eclipse.tv

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastAlbum = ""
    private var lastArtwork = ""
    private var lastCapturedAudioUrl = ""

    private val metadataPoll = object : Runnable {
        override fun run() {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    """(function(){
                        const m = navigator.mediaSession && navigator.mediaSession.metadata;
                        const title = (m && m.title) || '';
                        const artist = (m && m.artist) || '';
                        const album = (m && m.album) || '';
                        const artwork = (m && m.artwork && m.artwork.length) ? (m.artwork[m.artwork.length - 1].src || '') : '';
                        return JSON.stringify({t:title,a:artist,al:album,art:artwork});
                    })()""".trimIndent()
                ) { raw ->
                    try {
                        val decoded = JSONObject(raw).optString("t")
                        val artist = JSONObject(raw).optString("a")
                        val album = JSONObject(raw).optString("al")
                        val artwork = JSONObject(raw).optString("art")
                        publishMetadata(decoded, artist, album, artwork)
                    } catch (_: Exception) {
                        // WebView may return an escaped JSON string on some Chromium builds.
                        try {
                            val decoded = raw.removePrefix("\"").removeSuffix("\"")
                                .replace("\\\"", "\"").replace("\\\\", "\\")
                            val json = JSONObject(decoded)
                            publishMetadata(
                                json.optString("t"),
                                json.optString("a"),
                                json.optString("al"),
                                json.optString("art")
                            )
                        } catch (_: Exception) { }
                    }
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun publishMetadata(titleRaw: String, artistRaw: String, albumRaw: String, artworkRaw: String) {
        val title = titleRaw.trim()
        val artist = artistRaw.trim()
        val album = albumRaw.trim()
        val artwork = artworkRaw.trim()
        if (title.isBlank() || title.equals("Eclipse Music", true) || title.equals("Eclipse TV", true)) return

        val effectiveArtwork = artwork.ifBlank { lastArtwork }
        val changed = title != lastTitle || artist != lastArtist || album != lastAlbum || effectiveArtwork != lastArtwork
        if (!changed) return

        lastTitle = title
        lastArtist = artist
        lastAlbum = album
        if (artwork.isNotBlank()) lastArtwork = artwork
        PlaybackBridge.updateMetadata(this, title, artist, effectiveArtwork)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        configureWebView()
        PlaybackBridge.connect(this)

        if (savedInstanceState == null) {
            webView.loadUrl("https://eclipsemusic.app/web/")
        } else {
            webView.restoreState(savedInstanceState)
        }
        handler.post(metadataPoll)
    }

    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.userAgentString = settings.userAgentString + " EclipseTV/1.8"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)
        webView.webViewClient = StreamCaptureClient { url -> startNativePlayback(url) }
        webView.webChromeClient = WebChromeClient()
    }

    private fun startNativePlayback(url: String) {
        if (url == lastCapturedAudioUrl) return
        lastCapturedAudioUrl = url

        webView.evaluateJavascript(
            "document.querySelectorAll('audio,video').forEach(e=>{try{e.pause();e.currentTime=0;e.muted=true;}catch(_){}});",
            null
        )

        val title = lastTitle.takeIf { it.isNotBlank() }
        val artist = lastArtist.takeIf { it.isNotBlank() }
        PlaybackBridge.playUrl(this, url, title, artist, lastArtwork.takeIf { it.isNotBlank() })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && ::webView.isInitialized) {
            if (RemotePlaybackController.handle(this, webView, event.keyCode)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
            keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
        PlaybackBridge.release()
        super.onDestroy()
    }
}
