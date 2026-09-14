package app.eclipse.tv

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem

object NowPlayingStore {
    const val ACTION_NOW_PLAYING = "app.eclipse.tv.NOW_PLAYING_CHANGED"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ALBUM = "album"
    const val EXTRA_ART_URI = "artUri"
    const val EXTRA_IS_PLAYING = "isPlaying"

    fun publish(context: Context, item: MediaItem?, isPlaying: Boolean = false) {
        val md = item?.mediaMetadata ?: return
        context.sendBroadcast(Intent(ACTION_NOW_PLAYING).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_TITLE, md.title?.toString() ?: "")
            putExtra(EXTRA_ARTIST, md.artist?.toString() ?: "")
            putExtra(EXTRA_ALBUM, md.albumTitle?.toString() ?: "")
            putExtra(EXTRA_ART_URI, md.artworkUri?.toString() ?: "")
            putExtra(EXTRA_IS_PLAYING, isPlaying)
        })
    }
}
