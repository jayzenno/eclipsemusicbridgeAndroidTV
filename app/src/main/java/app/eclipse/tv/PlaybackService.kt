package app.eclipse.tv

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_500,
                12_000,
                250,
                750
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exo ->
                mediaSession = MediaSession.Builder(this, exo).build()
                exo.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                        NowPlayingStore.publish(this@PlaybackService, item, exo.isPlaying)
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem, isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem, exo.isPlaying)
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
