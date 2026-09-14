package app.eclipse.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private lateinit var nowPlaying: TvNowPlayingOverlay
    private val handler = Handler(Looper.getMainLooper())
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastArtwork = ""

    private val nowPlayingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NowPlayingStore.ACTION_NOW_PLAYING) return
            val title = intent.getStringExtra(NowPlayingStore.EXTRA_TITLE).orEmpty()
            val artist = intent.getStringExtra(NowPlayingStore.EXTRA_ARTIST).orEmpty()
            val album = intent.getStringExtra(NowPlayingStore.EXTRA_ALBUM).orEmpty()
            val art = intent.getStringExtra(NowPlayingStore.EXTRA_ART_URI).orEmpty()
            val playing = intent.getBooleanExtra(NowPlayingStore.EXTRA_IS_PLAYING, true)
            if (title.isNotBlank()) {
                lastTitle = title
                lastArtist = artist
                if (art.isNotBlank()) lastArtwork = art
                nowPlaying.show(title, artist, album, art.ifBlank { lastArtwork }, playing)
            }
        }
    }

    private val metadataPoll = object : Runnable {
        override fun run() {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    """(function(){
                        const m=navigator.mediaSession&&navigator.mediaSession.metadata;
                        const mt=m&&m.title?m.title:'';
                        const ma=m&&m.artist?m.artist:'';
                        const mal=m&&m.album?m.album:'';
                        const aw=m&&m.artwork&&m.artwork.length?m.artwork[0].src:'';
                        const title=mt||document.querySelector('[data-testid*=\"title\" i],[class*=\"track-title\" i],[class*=\"song-title\" i]')?.textContent||document.querySelector('meta[property=\"og:title\"]')?.content||document.title||'';
                        const artist=ma||document.querySelector('[data-testid*=\"artist\" i],[class*=\"artist\" i],[class*=\"song-artist\" i]')?.textContent||'';
                        return JSON.stringify({t:title.trim(),a:artist.trim(),al:mal.trim(),art:aw||''});
                    })()""".trimIndent()
                ) { raw ->
                    try {
                        val decoded = raw.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                        val json = JSONObject(decoded)
                        val title = json.optString("t").trim()
                        val artist = json.optString("a").trim()
                        val album = json.optString("al").trim()
                        val artwork = json.optString("art").trim()
                        if (title.isNotEmpty() && title != "Eclipse Music" && title != "Eclipse TV") {
                            if (artwork.isNotBlank()) lastArtwork = artwork
                            nowPlaying.show(title, artist, album, artwork.ifBlank { lastArtwork }, true)
                            if (title != lastTitle || artist != lastArtist || artwork != lastArtwork) {
                                lastTitle = title
                                lastArtist = artist
                                PlaybackBridge.updateMetadata(this@MainActivity, title, artist, artwork.ifBlank { lastArtwork })
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
            handler.postDelayed(this, 700)
        }
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
        nowPlaying = TvNowPlayingOverlay(root)
        setContentView(root)
        configureWebView()
        PlaybackBridge.connect(this)

        registerReceiver(nowPlayingReceiver, IntentFilter(NowPlayingStore.ACTION_NOW_PLAYING), Context.RECEIVER_NOT_EXPORTED)

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
        val title = lastTitle.takeIf { it.isNotBlank() }
        val artist = lastArtist.takeIf { it.isNotBlank() }
        PlaybackBridge.playUrl(this, url, title, artist, lastArtwork.takeIf { it.isNotBlank() })
        handler.postDelayed({
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "document.querySelectorAll('audio,video').forEach(e=>{e.pause();e.muted=true;});",
                    null
                )
            }
        }, 150)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (RemotePlaybackController.handle(this, webView, keyCode)) return true
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
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { unregisterReceiver(nowPlayingReceiver) } catch (_: Exception) { }
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
        nowPlaying.destroy()
        PlaybackBridge.release()
        super.onDestroy()
    }
}
