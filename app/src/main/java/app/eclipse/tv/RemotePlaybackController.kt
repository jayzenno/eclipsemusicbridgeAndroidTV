package app.eclipse.tv

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.webkit.WebView

object RemotePlaybackController {
    private var lastTrackActionAt = 0L

    fun handle(context: Context, webView: WebView, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                PlaybackBridge.playPause(context)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                PlaybackBridge.play(context)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                PlaybackBridge.pause(context)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                triggerTrackAction(webView, true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                triggerTrackAction(webView, false)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                PlaybackBridge.seekBy(context, -10_000L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                PlaybackBridge.seekBy(context, 10_000L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                webView.post { webView.evaluateJavascript("document.activeElement?.click();", null) }
                return true
            }
            else -> return false
        }
    }

    fun triggerTrackAction(webView: WebView, next: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTrackActionAt < 180L) return
        lastTrackActionAt = now
        val script = if (next) {
            """(function(){
                const selectors=['[aria-label*=\"Next\" i]','[aria-label*=\"Nächster\" i]','[aria-label*=\"Weiter\" i]','[data-testid*=\"next\" i]','button[title*=\"Next\" i]','button[title*=\"Weiter\" i]'];
                const el=selectors.map(s=>document.querySelector(s)).find(Boolean);
                if(el){el.click();return 'clicked';} return 'missing';
            })()""".trimIndent()
        } else {
            """(function(){
                const selectors=['[aria-label*=\"Previous\" i]','[aria-label*=\"Zurück\" i]','[aria-label*=\"Back\" i]','[data-testid*=\"previous\" i]','button[title*=\"Previous\" i]','button[title*=\"Zurück\" i]'];
                const el=selectors.map(s=>document.querySelector(s)).find(Boolean);
                if(el){el.click();return 'clicked';} return 'missing';
            })()""".trimIndent()
        }
        webView.post { webView.evaluateJavascript(script, null) }
    }
}
