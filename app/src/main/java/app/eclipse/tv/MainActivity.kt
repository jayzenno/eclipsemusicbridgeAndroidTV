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
import org.json.JSONTokener

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var streamCaptureClient: StreamCaptureClient
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        webView = WebView(this)
        setContentView(webView)
        configureWebView()
        PlaybackBridge.connect(this)

        if (savedInstanceState == null) webView.loadUrl("https://eclipsemusic.app/web/")
        else webView.restoreState(savedInstanceState)
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
        settings.userAgentString = settings.userAgentString + " EclipseTV/1.4"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus(View.FOCUS_DOWN)

        streamCaptureClient = StreamCaptureClient(
            onAudioUrl = { url -> startNativePlayback(url) },
            onPageReady = { injectTvNavigation() }
        )
        webView.webViewClient = streamCaptureClient
        webView.webChromeClient = WebChromeClient()
    }

    private fun startNativePlayback(url: String) {
        android.util.Log.d("EclipseTV", "NATIVE_PLAYBACK $url")

        // Pull whatever metadata Eclipse already exposes in the page. This makes the
        // native MediaSession useful to TV launchers without requiring Eclipse changes.
        val metadataScript = """
            (function(){
              function meta(name){
                var e=document.querySelector('meta[property="'+name+'"],meta[name="'+name+'"]');
                return e ? e.content : '';
              }
              return JSON.stringify({
                title: document.title || '',
                artist: meta('music:musician') || meta('author') || '',
                album: meta('music:album') || '',
                artwork: meta('og:image') || ''
              });
            })();
        """.trimIndent()

        webView.evaluateJavascript(metadataScript) { raw ->
            val metadata = runCatching {
                JSONTokener(raw).nextValue().toString().let(::org.json.JSONObject)
            }.getOrNull()

            PlaybackBridge.playUrl(
                this,
                url,
                metadata?.optString("title")?.takeIf { it.isNotBlank() },
                metadata?.optString("artist")?.takeIf { it.isNotBlank() },
                metadata?.optString("album")?.takeIf { it.isNotBlank() },
                metadata?.optString("artwork")?.takeIf { it.startsWith("http") }
            )
        }

        // The WebView must not decode the same stream in parallel with ExoPlayer.
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                webView.evaluateJavascript(
                    "document.querySelectorAll('audio,video').forEach(function(e){e.pause();e.muted=true;});",
                    null
                )
            }
        }, 250L)
    }

    private fun injectTvNavigation() {
        val script = """
            (function(){
              if(document.getElementById('eclipse-tv-style')) return;
              var s=document.createElement('style');
              s.id='eclipse-tv-style';
              s.textContent=`
                html,body{overscroll-behavior:none;}
                button,a,[role="button"],[tabindex]{outline-offset:6px;}
                button:focus,a:focus,[role="button"]:focus,[tabindex]:focus{
                  outline:3px solid currentColor !important;
                  outline-offset:6px !important;
                }
                button,[role="button"]{min-height:44px;}
              `;
              document.head.appendChild(s);
              document.documentElement.style.setProperty('scroll-behavior','smooth');
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                PlaybackBridge.playPause(this)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                PlaybackBridge.next(this)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                PlaybackBridge.previous(this)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        streamCaptureClient.release()
        webView.destroy()
        // Deliberately keep the MediaSession/ExoPlayer alive. Background audio must
        // survive the Activity leaving the foreground.
        super.onDestroy()
    }
}
