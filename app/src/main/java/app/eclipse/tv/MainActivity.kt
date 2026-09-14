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
import org.json.JSONObject
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
        settings.userAgentString = settings.userAgentString + " EclipseTV/1.5"
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
        PlaybackBridge.playUrl(this, url)

        val metadataScript = """
            (function(){
              function meta(name){
                var e=document.querySelector('meta[property="'+name+'"],meta[name="'+name+'"]');
                return e ? e.content : '';
              }
              var title = '';
              var artist = '';
              var album = '';
              var artwork = meta('og:image') || '';
              var titleSelectors = [
                '[data-testid*="title"]','[data-test*="title"]','.track-title','.song-title',
                '[class*="track-title"]','[class*="song-title"]','[aria-label*="song"]'
              ];
              for(var i=0;i<titleSelectors.length && !title;i++){
                var e=document.querySelector(titleSelectors[i]);
                if(e) title=(e.innerText||e.textContent||'').trim();
              }
              title = title || (document.title || '');
              artist = meta('music:musician') || meta('author') || '';
              album = meta('music:album') || '';
              return JSON.stringify({title:title,artist:artist,album:album,artwork:artwork});
            })();
        """.trimIndent()

        webView.evaluateJavascript(metadataScript) { raw ->
            val metadata = runCatching {
                val jsonText = JSONTokener(raw).nextValue() as? String ?: return@runCatching null
                JSONObject(jsonText)
            }.getOrNull() ?: return@evaluateJavascript

            PlaybackBridge.updateMetadata(
                this,
                metadata.optString("title").takeIf { it.isNotBlank() },
                metadata.optString("artist").takeIf { it.isNotBlank() },
                metadata.optString("album").takeIf { it.isNotBlank() },
                metadata.optString("artwork").takeIf { it.startsWith("http") }
            )
        }

        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                webView.evaluateJavascript(
                    "document.querySelectorAll('audio,video').forEach(function(e){e.pause();e.muted=true;});",
                    null
                )
            }
        }, 80L)
    }

    private fun dispatchEclipseTransport(action: String) {
        val script = """
            (function(){
              var action='$action';
              var labels = action==='next'
                ? ['next','skip next','next track','weiter','nächster','naechster']
                : ['previous','prev','skip previous','previous track','zurück','zurueck','vorheriger'];
              var nodes = Array.prototype.slice.call(document.querySelectorAll('button,[role="button"],a,[aria-label],[title]'));
              function text(n){return ((n.getAttribute('aria-label')||'')+' '+(n.getAttribute('title')||'')+' '+(n.innerText||'')).toLowerCase().trim();}
              for(var i=0;i<nodes.length;i++){
                var t=text(nodes[i]);
                for(var j=0;j<labels.length;j++){
                  if(t===labels[j] || t.indexOf(labels[j])!==-1){
                    nodes[i].click();
                    return 'true';
                  }
                }
              }
              return 'false';
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { result ->
            if (result != "\"true\"") {
                if (action == "next") PlaybackBridge.next(this) else PlaybackBridge.previous(this)
            }
        }
    }

    private fun injectTvNavigation() {
        val script = """
            (function(){
              if(document.getElementById('eclipse-tv-style')) return;
              var s=document.createElement('style');
              s.id='eclipse-tv-style';
              s.textContent=`
                html,body{overscroll-behavior:none;scroll-behavior:auto !important;}
                body{padding:0 !important;}
                button,a,[role="button"],[tabindex]{outline-offset:7px;}
                button:focus,a:focus,[role="button"]:focus,[tabindex]:focus{
                  outline:3px solid currentColor !important;
                  outline-offset:7px !important;
                  transform:scale(1.035);
                  transition:transform 80ms linear;
                  z-index:20 !important;
                  position:relative;
                }
                button,[role="button"]{min-height:48px;min-width:48px;}
                input,select{min-height:48px;font-size:16px;}
                img{image-rendering:auto;}
                [class*="card"],[class*="Card"],[class*="tile"],[class*="Tile"]{scroll-margin:72px;}
              `;
              document.head.appendChild(s);
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
                dispatchEclipseTransport("next")
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                dispatchEclipseTransport("previous")
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
