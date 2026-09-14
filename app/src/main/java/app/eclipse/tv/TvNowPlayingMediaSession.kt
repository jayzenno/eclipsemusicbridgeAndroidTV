package app.eclipse.tv

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class TvNowPlayingMediaSession(context: Context, player: Player) {
    val session = MediaSession.Builder(context, player)
        .setCallback(object : MediaSession.Callback {})
        .build()

    fun update(item: MediaItem?) {
        if (item != null) session.player.setMediaItem(item)
        session.player.prepare()
    }

    fun release() = session.release()
}
