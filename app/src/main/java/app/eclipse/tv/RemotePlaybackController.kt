package app.eclipse.tv

import android.webkit.WebView

object RemotePlaybackController {
    fun handle(webView: WebView, keyCode: Int): Boolean {
        val js = when (keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "document.querySelectorAll('audio,video').forEach(e=>e.paused?e.play():e.pause());"
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "document.querySelectorAll('audio,video').forEach(e=>e.play());"
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "document.querySelectorAll('audio,video').forEach(e=>e.pause());"
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "document.querySelector('[aria-label*=\"Next\" i],[aria-label*=\"Nächster\" i],[data-testid*=\"next\" i]')?.click();"
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "document.querySelector('[aria-label*=\"Previous\" i],[aria-label*=\"Zurück\" i],[data-testid*=\"previous\" i]')?.click();"
            android.view.KeyEvent.KEYCODE_DPAD_CENTER -> "document.activeElement?.click();"
            else -> return false
        }
        webView.post { webView.evaluateJavascript(js, null) }
        return true
    }
}
