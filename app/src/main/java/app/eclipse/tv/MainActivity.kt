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
            if (::webView.isInitialized) captureMetadata(null)
            handler.postDelayed(this, 350)
        }
    }

    private fun captureMetadata(after: ((String?, String?, String?, String?) -> Unit)?) {
        webView.evaluateJavascript(
            """(function(){
                const clean=v=>(v||'').replace(/\\s+/g,' ').trim();
                const text=el=>clean(el&&(el.textContent||el.getAttribute('content')||el.getAttribute('aria-label')||el.getAttribute('title')));
                const first=(selectors)=>{
                    for(const s of selectors){
                        try { const el=document.querySelector(s); const v=text(el); if(v) return v; } catch(_) {}
                    }
                    return '';
                };
                const m=navigator.mediaSession&&navigator.mediaSession.metadata;
                const media=document.querySelector('audio,video');
                const artwork=(m&&m.artwork&&m.artwork.length)
                    ? (m.artwork[m.artwork.length-1].src||'')
                    : ((media&&(media.getAttribute('poster')||media.getAttribute('data-artwork')))||'');
                const title=clean(m&&m.title)||
                    first([
                        '[data-testid*=\"track-title\" i]','[data-testid*=\"song-title\" i]',
                        '[data-testid*=\"now-playing-title\" i]','[data-testid*=\"current-track\" i]',
                        '[class*=\"track-title\" i]','[class*=\"song-title\" i]',
                        '[class*=\"now-playing\" i] [class*=\"title\" i]',
                        '[aria-label*=\"Now playing\" i]'
                    ]) || clean(document.querySelector('meta[property=\"og:title\"]')?.content);
                const artist=clean(m&&m.artist)||
                    first([
                        '[data-testid*=\"track-artist\" i]','[data-testid*=\"song-artist\" i]',
                        '[data-testid*=\"now-playing-artist\" i]','[class*=\"track-artist\" i]',
                        '[class*=\"song-artist\" i]','[class*=\"now-playing\" i] [class*=\"artist\" i]'
                    ]);
                const album=clean(m&&m.album)||first(['[data-testid*=\"track-album\" i]','[class*=\"track-album\" i]']);
                const generic=/^(eclipse|eclipse music|eclipse tv|music player)$/i;
                const t=generic.test(title)?'':title;
                return JSON.stringify({t:t,a:artist,al:album,art:artwork});
            })()""".trimIndent()
        ) { raw ->
            try {
                val json = decodeJsJson(raw)
                val title = json.optString("t").trim()
                val artist = json.optString("a").trim()
                val album = json.optString("al").trim()
                val artwork = json.optString("art").trim()
                if (after != null) {
                    after(title.takeIf { it.isNotBlank() }, artist.takeIf { it.isNotBlank() }, album.takeIf { it.isNotBlank() }, artwork.takeIf { it.isNotBlank() })
                }
                publishMetadata(title, artist, album, artwork)
            } catch (_: Exception) {
                after?.invoke(null, null, null, null)
            }
        }
    }

    private fun decodeJsJson(raw: String): JSONObject {
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            JSONObject(raw.removePrefix("\"").removeSuffix("\"").replace("\\\"", "\"").replace("\\\\", "\\"))
        }
    }

    private fun publishMetadata(titleRaw: String, artistRaw: String, albumRaw: String, artworkRaw: String) {
        val title = titleRaw.trim()
        val artist = artistRaw.trim()
        val album = albumRaw.trim()
        val artwork = artworkRaw.trim()
        if (title.isBlank()) return
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

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://eclipsemusic.app/web/")
        }
        webView.requestFocus(View.FOCUS_DOWN)
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
        settings.userAgentString = settings.userAgentString + " EclipseTV/1.9"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.webViewClient = StreamCaptureClient { url -> startNativePlayback(url) }
        webView.webChromeClient = WebChromeClient()
    }

    private fun startNativePlayback(url: String) {
        if (url == lastCapturedAudioUrl) return
        lastCapturedAudioUrl = url
        webView.evaluateJavascript("document.querySelectorAll('audio,video').forEach(e=>{try{e.pause();e.currentTime=0;e.muted=true;}catch(_){}});", null)
        captureMetadata { title, artist, _, artwork ->
            val effectiveTitle = title ?: lastTitle.takeIf { it.isNotBlank() }
            val effectiveArtist = artist ?: lastArtist.takeIf { it.isNotBlank() }
            val effectiveArtwork = artwork ?: lastArtwork.takeIf { it.isNotBlank() }
            PlaybackBridge.playUrl(this, url, effectiveTitle, effectiveArtist, effectiveArtwork)
        }
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
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
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
