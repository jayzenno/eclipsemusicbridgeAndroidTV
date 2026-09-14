package app.eclipse.tv

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class TvNowPlayingOverlay(private val root: LinearLayout) {
    private val title = TextView(root.context).apply { textSize = 22f; setTextColor(Color.WHITE) }
    private val artist = TextView(root.context).apply { textSize = 15f; setTextColor(0xffbdbdbd.toInt()) }

    init {
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.BOTTOM
        root.setPadding(48, 24, 48, 32)
        root.addView(title)
        root.addView(artist)
        root.visibility = View.GONE
    }

    fun show(songTitle: String, songArtist: String) {
        title.text = songTitle
        artist.text = songArtist
        root.visibility = View.VISIBLE
    }

    fun hide() { root.visibility = View.GONE }
}
