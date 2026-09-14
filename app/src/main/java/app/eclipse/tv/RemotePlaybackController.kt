package app.eclipse.tv

import android.content.Context
import android.view.KeyEvent
import android.webkit.WebView

object RemotePlaybackController {
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
                triggerWebPlayerAction(webView, next = true)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                triggerWebPlayerAction(webView, next = false)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                webView.post { webView.evaluateJavascript("document.activeElement?.click();", null) }
                return true
            }
            else -> return false
        }
    }

    private fun triggerWebPlayerAction(webView: WebView, next: Boolean) {
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
