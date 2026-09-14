package app.eclipse.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var nowPlaying: LinearLayout
    private lateinit var nowTitle: TextView
    private lateinit var nowArtist: TextView
    private lateinit var nowStatus: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastTitle = ""
    private var lastArtist = ""

    private val nowPlayingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NowPlayingStore.ACTION_NOW_PLAYING) return
            val title = intent.getStringExtra(NowPlayingStore.EXTRA_TITLE).orEmpty()
            val artist = intent.getStringExtra(NowPlayingStore.EXTRA_ARTIST).orEmpty()
            if (title.isNotBlank()) showNowPlaying(title, artist)
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
                        const title=mt||document.querySelector('[data-testid*=\"title\" i],[class*=\"track-title\" i],[class*=\"song-title\" i]')?.textContent||document.querySelector('meta[property=\"og:title\"]')?.content||document.title||'';
                        const artist=ma||document.querySelector('[data-testid*=\"artist\" i],[class*=\"artist\" i],[class*=\"song-artist\" i]')?.textContent||'';
                        return JSON.stringify({t:title.trim(),a:artist.trim()});
                    })()""".trimIndent()
                ) { raw ->
                    try {
                        val decoded = raw.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"")
                        val json = JSONObject(decoded)
                        val title = json.optString("t").trim()
                        val artist = json.optString("a").trim()
                        if (title.isNotEmpty() && title != "Eclipse Music" && title != "Eclipse TV") {
                            showNowPlaying(title, artist)
                            if (title != lastTitle || artist != lastArtist) {
                                lastTitle = title
                                lastArtist = artist
                                PlaybackBridge.updateMetadata(this@MainActivity, title, artist)
                            }
                        }
                    } catch (_: Exception) { }
                }
            }
            handler.postDelayed(this, 500)
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
        buildNowPlayingOverlay(root)
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

    private fun buildNowPlayingOverlay(root: FrameLayout) {
        nowPlaying = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 24, 44, 24)
            setBackgroundColor(0xe6101014.toInt())
            elevation = 18f
        }
        val lp = FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(32, 0, 32, 32)
        root.addView(nowPlaying, lp)

        val brand = TextView(this).apply {
            text = "ECLIPSE TV   •   NOW PLAYING"
            textSize = 12f
            setTextColor(0xffa7a7b0.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        nowTitle = TextView(this).apply {
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 5, 0, 0)
        }
        nowArtist = TextView(this).apply {
            textSize = 18f
            setTextColor(0xffc7c7cf.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        nowStatus = TextView(this).apply {
            text = "●  PLAYING"
            textSize = 12f
            setTextColor(0xffdddddd.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 12, 0, 0)
        }
        nowPlaying.addView(brand)
        nowPlaying.addView(nowTitle)
        nowPlaying.addView(nowArtist)
        nowPlaying.addView(nowStatus)
        nowPlaying.visibility = View.GONE
    }

    private fun showNowPlaying(title: String, artist: String) {
        nowTitle.text = title
        nowArtist.text = artist
        nowArtist.visibility = if (artist.isBlank()) View.GONE else View.VISIBLE
        nowStatus.text = "●  PLAYING"
        nowPlaying.visibility = View.VISIBLE
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
        settings.userAgentString = settings.userAgentString + " EclipseTV/1.7"
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
        PlaybackBridge.playUrl(this, url, title, artist)
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
        PlaybackBridge.release()
        super.onDestroy()
    }
}
