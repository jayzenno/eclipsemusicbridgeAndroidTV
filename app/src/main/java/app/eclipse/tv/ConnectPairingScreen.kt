package app.eclipse.tv

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Connect entry point. The actual Eclipse Connect handshake is intentionally
 * delegated to the web session until an official pairing endpoint is exposed.
 */
class ConnectPairingScreen(private val root: LinearLayout) {
    init {
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setPadding(80, 60, 80, 60)
        root.setBackgroundColor(Color.BLACK)
        val title = TextView(root.context).apply {
            text = "ECLIPSE TV"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val body = TextView(root.context).apply {
            text = "Öffne Eclipse Connect auf deinem Handy\nund wähle dieses TV-Gerät aus.\n\nAlternativ kannst du dich auf dem TV anmelden."
            textSize = 19f
            setTextColor(0xffdddddd.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 0)
        }
        root.addView(title)
        root.addView(body)
    }
}
