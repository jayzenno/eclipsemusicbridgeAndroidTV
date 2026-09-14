package app.eclipse.tv

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@UnstableApi
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                60_000,
                1_000,
                2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setAudioAttributes(audioAttributes, true)
                playWhenReady = false
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) retryCount = 0
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (retryCount >= 2) return
                        retryCount++
                        val delay = 500L * retryCount
                        android.util.Log.w(
                            "EclipseTV",
                            "Transient playback error, retry $retryCount in ${delay}ms",
                            error
                        )
                        retryHandler.postDelayed({
                            if (mediaSession?.player === this@apply) {
                                prepare()
                                play()
                            }
                        }, delay)
                    }
                })
            }

        mediaSession = MediaSession.Builder(this, player)
            .setId("eclipse-tv-session")
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        retryHandler.removeCallbacksAndMessages(null)
        mediaSession?.let { session ->
            session.player.stop()
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
