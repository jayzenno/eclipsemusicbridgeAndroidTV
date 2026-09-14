package app.eclipse.tv

import android.content.Context
import android.view.KeyEvent
import android.webkit.WebView

object RemotePlaybackController {
    fun handle(context: Context, webView: WebView, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { PlaybackBridge.playPause(context); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { webView.post { webView.evaluateJavascript("document.querySelectorAll('audio,video').forEach(e=>e.play());", null) }; return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { PlaybackBridge.playPause(context); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { webView.post { webView.evaluateJavascript("document.querySelector('[aria-label*=\"Next\" i],[aria-label*=\"Nächster\" i],[data-testid*=\"next\" i]')?.click();", null) }; return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { webView.post { webView.evaluateJavascript("document.querySelector('[aria-label*=\"Previous\" i],[aria-label*=\"Zurück\" i],[data-testid*=\"previous\" i]')?.click();", null) }; return true }
            KeyEvent.KEYCODE_DPAD_CENTER -> { webView.post { webView.evaluateJavascript("document.activeElement?.click();", null) }; return true }
            else -> return false
        }
    }
}
