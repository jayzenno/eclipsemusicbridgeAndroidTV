package app.eclipse.tv

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors

class TvNowPlayingOverlay(private val root: FrameLayout) {
    private val executor = Executors.newSingleThreadExecutor()
    private val panel = LinearLayout(root.context)
    private val artwork = ImageView(root.context)
    private val title = TextView(root.context)
    private val artist = TextView(root.context)
    private val album = TextView(root.context)
    private val status = TextView(root.context)
    private val hint = TextView(root.context)

    init {
        panel.orientation = LinearLayout.HORIZONTAL
        panel.gravity = Gravity.CENTER_VERTICAL
        panel.setPadding(36, 24, 36, 24)
        panel.setBackgroundColor(0xee101014.toInt())
        panel.elevation = 24f

        artwork.scaleType = ImageView.ScaleType.CENTER_CROP
        artwork.setBackgroundColor(0xff24242a.toInt())
        panel.addView(artwork, LinearLayout.LayoutParams(112, 112))

        val text = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 0, 20, 0)
        }
        title.textSize = 26f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END

        artist.textSize = 18f
        artist.setTextColor(0xffc7c7cf.toInt())
        artist.maxLines = 1
        artist.ellipsize = android.text.TextUtils.TruncateAt.END

        album.textSize = 14f
        album.setTextColor(0xff92929b.toInt())
        album.maxLines = 1
        album.ellipsize = android.text.TextUtils.TruncateAt.END

        status.textSize = 12f
        status.setTextColor(0xffdddddd.toInt())
        status.typeface = Typeface.DEFAULT_BOLD
        status.setPadding(0, 10, 0, 0)

        text.addView(title)
        text.addView(artist)
        text.addView(album)
        text.addView(status)
        panel.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

        hint.text = "‹   ▶ / ❚❚   ›"
        hint.textSize = 19f
        hint.setTextColor(Color.WHITE)
        hint.typeface = Typeface.DEFAULT_BOLD
        hint.gravity = Gravity.CENTER
        hint.setPadding(20, 0, 8, 0)
        panel.addView(hint, LinearLayout.LayoutParams(170, -1))

        val lp = FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(32, 0, 32, 32)
        root.addView(panel, lp)
        panel.visibility = View.GONE
    }

    fun show(songTitle: String, songArtist: String, songAlbum: String = "", artUri: String = "", isPlaying: Boolean = true) {
        title.text = songTitle
        artist.text = songArtist
        album.text = songAlbum
        album.visibility = if (songAlbum.isBlank()) View.GONE else View.VISIBLE
        status.text = if (isPlaying) "●  PLAYING" else "Ⅱ  PAUSED"
        panel.visibility = View.VISIBLE
        if (artUri.isNotBlank()) loadArtwork(artUri) else artwork.setImageDrawable(null)
    }

    fun hide() { panel.visibility = View.GONE }

    fun destroy() { executor.shutdownNow() }

    private fun loadArtwork(uri: String) {
        executor.execute {
            try {
                val bitmap = BitmapFactory.decodeStream(URL(Uri.parse(uri).toString()).openStream())
                if (bitmap != null) root.post { if (panel.visibility == View.VISIBLE) artwork.setImageBitmap(bitmap) }
            } catch (_: Exception) { }
        }
    }
}
