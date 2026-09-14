package app.eclipse.tv

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().also { exo ->
            mediaSession = MediaSession.Builder(this, exo).build()
            exo.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                    NowPlayingStore.publish(this@PlaybackService, item)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem)
                }
            })
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
