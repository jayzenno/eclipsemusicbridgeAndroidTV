package app.eclipse.tv

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max

class TvNowPlayingOverlay(private val root: FrameLayout) {
    private val executor = Executors.newSingleThreadExecutor()
    private val panel = LinearLayout(root.context)
    private val artwork = ImageView(root.context)
    private val title = TextView(root.context)
    private val artist = TextView(root.context)
    private val album = TextView(root.context)
    private val status = TextView(root.context)
    private val progress = ProgressBar(root.context, null, android.R.attr.progressBarStyleHorizontal)
    private val elapsed = TextView(root.context)
    private val duration = TextView(root.context)
    private val play = ImageButton(root.context)
    private val previous = ImageButton(root.context)
    private val next = ImageButton(root.context)
    private var visible = false
    private var hideAnimator: ValueAnimator? = null

    init {
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(36, 28, 36, 28)
        panel.setBackgroundColor(0xf2121217.toInt())
        panel.elevation = 28f
        panel.isFocusable = true
        panel.isFocusableInTouchMode = true

        val row = LinearLayout(root.context).apply { gravity = Gravity.CENTER_VERTICAL }
        artwork.scaleType = ImageView.ScaleType.CENTER_CROP
        artwork.setBackgroundColor(0xff28282f.toInt())
        row.addView(artwork, LinearLayout.LayoutParams(116, 116))

        val text = LinearLayout(root.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 0, 12, 0)
        }
        title.textSize = 27f
        title.setTextColor(Color.WHITE)
        title.typeface = Typeface.DEFAULT_BOLD
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        artist.textSize = 18f
        artist.setTextColor(0xffd0d0d8.toInt())
        artist.maxLines = 1
        artist.ellipsize = android.text.TextUtils.TruncateAt.END
        album.textSize = 14f
        album.setTextColor(0xff9999a2.toInt())
        album.maxLines = 1
        album.ellipsize = android.text.TextUtils.TruncateAt.END
        status.textSize = 12f
        status.setTextColor(0xffdddddd.toInt())
        status.typeface = Typeface.DEFAULT_BOLD
        status.setPadding(0, 9, 0, 0)
        text.addView(title)
        text.addView(artist)
        text.addView(album)
        text.addView(status)
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))

        val controls = LinearLayout(root.context).apply { gravity = Gravity.CENTER_VERTICAL }
        previous = button(android.R.drawable.ic_media_previous, "Previous")
        play = button(android.R.drawable.ic_media_play, "Play")
        next = button(android.R.drawable.ic_media_next, "Next")
        controls.addView(previous)
        controls.addView(play)
        controls.addView(next)
        row.addView(controls, LinearLayout.LayoutParams(190, -1))
        panel.addView(row)

        val timeRow = LinearLayout(root.context).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 14, 0, 0) }
        elapsed.textSize = 12f
        elapsed.setTextColor(0xffaaaaaf.toInt())
        elapsed.text = "0:00"
        duration.textSize = 12f
        duration.setTextColor(0xffaaaaaf.toInt())
        duration.text = "0:00"
        progress.max = 1000
        progress.progress = 0
        progress.isFocusable = false
        timeRow.addView(elapsed, LinearLayout.LayoutParams(44, -2))
        timeRow.addView(progress, LinearLayout.LayoutParams(0, 20, 1f))
        timeRow.addView(duration, LinearLayout.LayoutParams(44, -2))
        panel.addView(timeRow)

        previous.setOnClickListener { PlaybackBridge.previous(root.context); flash() }
        play.setOnClickListener { PlaybackBridge.playPause(root.context); flash() }
        next.setOnClickListener { PlaybackBridge.next(root.context); flash() }

        val lp = FrameLayout.LayoutParams(-1, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        lp.setMargins(32, 0, 32, 32)
        root.addView(panel, lp)
        panel.visibility = View.GONE
    }

    private fun button(icon: Int, description: String) = ImageButton(root.context).apply {
        setImageResource(icon)
        contentDescription = description
        setColorFilter(Color.WHITE)
        background = null
        isFocusable = true
        isFocusableInTouchMode = true
        setPadding(14, 14, 14, 14)
    }

    fun show(songTitle: String, songArtist: String, songAlbum: String = "", artUri: String = "", isPlaying: Boolean = true) {
        title.text = songTitle
        artist.text = songArtist
        album.text = songAlbum
        album.visibility = if (songAlbum.isBlank()) View.GONE else View.VISIBLE
        status.text = if (isPlaying) "●  PLAYING" else "Ⅱ  PAUSED"
        play.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
        if (artUri.isNotBlank()) loadArtwork(artUri)
        if (!visible) {
            visible = true
            panel.alpha = 0f
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).setDuration(180).start()
        }
        hideAnimator?.cancel()
        hideAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 4500
            addUpdateListener { panel.alpha = it.animatedValue as Float }
            start()
        }
    }

    fun updateProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        progress.progress = (positionMs.toDouble() / durationMs * 1000).toInt().coerceIn(0, 1000)
        elapsed.text = formatTime(positionMs)
        this.duration.text = formatTime(durationMs)
    }

    fun showPersistent() { visible = true; hideAnimator?.cancel(); panel.alpha = 1f; panel.visibility = View.VISIBLE; panel.requestFocus() }
    fun hide() { visible = false; hideAnimator?.cancel(); panel.animate().alpha(0f).setDuration(180).withEndAction { panel.visibility = View.GONE }.start() }
    fun isVisible() = visible
    fun destroy() { hideAnimator?.cancel(); executor.shutdownNow() }

    fun handleDpad(keyCode: Int): Boolean {
        if (!visible) return false
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return false
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { PlaybackBridge.previous(root.context); return true }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) { PlaybackBridge.next(root.context); return true }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) { PlaybackBridge.playPause(root.context); return true }
        return false
    }

    private fun flash() { panel.animate().scaleX(0.995f).scaleY(0.995f).setDuration(60).withEndAction { panel.animate().scaleX(1f).scaleY(1f).setDuration(60).start() }.start() }
    private fun formatTime(ms: Long): String { val total = max(0L, ms) / 1000; return "%d:%02d".format(total / 60, total % 60) }
    private fun loadArtwork(uri: String) { executor.execute { try { val bitmap = BitmapFactory.decodeStream(URL(Uri.parse(uri).toString()).openStream()); if (bitmap != null) root.post { artwork.setImageBitmap(bitmap) } } catch (_: Exception) {} } }
}
