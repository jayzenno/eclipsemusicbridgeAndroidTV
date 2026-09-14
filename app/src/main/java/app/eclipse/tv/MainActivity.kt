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

        val metadataScript = """
            (function(){
              function meta(name){
                var e=document.querySelector('meta[property="'+name+'"],meta[name="'+name+'"]');
                return e ? e.content : '';
              }
              function text(selectors){
                for(var i=0;i<selectors.length;i++){
                  var e=document.querySelector(selectors[i]);
                  if(e && e.textContent && e.textContent.trim()) return e.textContent.trim();
                }
                return '';
              }
              function image(){
                var e=document.querySelector('img[alt*="album" i],img[alt*="cover" i],img[src*="cover" i]');
                return e ? (e.currentSrc || e.src || '') : (meta('og:image') || '');
              }
              return JSON.stringify({
                title: text(['[data-testid*="track"]','[class*="track-title"]','[class*="song-title"]','[aria-label*="track" i]']) || document.title || '',
                artist: text(['[data-testid*="artist"]','[class*="artist"]','[aria-label*="artist" i]']) || meta('music:musician') || meta('author') || '',
                album: text(['[data-testid*="album"]','[class*="album"]','[aria-label*="album" i]']) || meta('music:album') || '',
                artwork: image()
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

        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                webView.evaluateJavascript(
                    "document.querySelectorAll('audio,video').forEach(function(e){e.pause();e.muted=true;});",
                    null
                )
            }
        }, 150L)
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
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun sendEclipseMediaCommand(command: String, fallback: () -> Unit) {
        val script = """
            (function(){
              var wanted = $command;
              var nodes = Array.prototype.slice.call(document.querySelectorAll('button,a,[role="button"]'));
              var hit = nodes.find(function(e){
                var label = ((e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')+' '+(e.textContent||'')).toLowerCase();
                return wanted.some(function(x){ return label.indexOf(x)>=0; });
              });
              if(hit){ hit.click(); return '1'; }
              return '0';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (result != "\"1\"") fallback()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                sendEclipseMediaCommand("['play','pause']") { PlaybackBridge.playPause(this) }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                sendEclipseMediaCommand("['next','skip forward','skip next']") { PlaybackBridge.next(this) }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                sendEclipseMediaCommand("['previous','prev','skip back','skip previous']") { PlaybackBridge.previous(this) }
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
        super.onDestroy()
    }
}
