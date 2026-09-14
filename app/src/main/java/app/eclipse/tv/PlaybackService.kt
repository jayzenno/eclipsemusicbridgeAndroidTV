package app.eclipse.tv

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val recoveryHandler = Handler(Looper.getMainLooper())
    private var recoveryAttempts = 0
    private var wasPlayingBeforeInterruption = false

    private val recoveryRunnable = Runnable {
        val exo = player ?: return@Runnable
        val item = exo.currentMediaItem ?: return@Runnable
        if (!wasPlayingBeforeInterruption || exo.isPlaying) return@Runnable

        val position = exo.currentPosition.coerceAtLeast(0L)
        exo.setMediaItem(item, position)
        exo.prepare()
        exo.play()
        recoveryAttempts++
    }

    override fun onCreate() {
        super.onCreate()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5_000,
                30_000,
                1_000,
                3_000
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
                exo.addListener(object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady) {
                            wasPlayingBeforeInterruption = true
                            recoveryAttempts = 0
                            recoveryHandler.removeCallbacks(recoveryRunnable)
                        } else if (exo.playbackState == Player.STATE_READY) {
                            // A normal user pause is STATE_READY and must never be auto-resumed.
                            wasPlayingBeforeInterruption = false
                            recoveryHandler.removeCallbacks(recoveryRunnable)
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying) {
                            wasPlayingBeforeInterruption = true
                            recoveryAttempts = 0
                            recoveryHandler.removeCallbacks(recoveryRunnable)
                        }
                        NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem, isPlaying)
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                if (wasPlayingBeforeInterruption) {
                                    recoveryHandler.removeCallbacks(recoveryRunnable)
                                    recoveryHandler.postDelayed(recoveryRunnable, 8_000L)
                                }
                            }
                            Player.STATE_READY -> {
                                recoveryHandler.removeCallbacks(recoveryRunnable)
                                recoveryAttempts = 0
                            }
                            Player.STATE_IDLE -> {
                                if (wasPlayingBeforeInterruption && recoveryAttempts < 3) {
                                    recoveryHandler.removeCallbacks(recoveryRunnable)
                                    recoveryHandler.postDelayed(recoveryRunnable, 1_000L)
                                }
                            }
                            Player.STATE_ENDED -> {
                                // Do not restart a track that genuinely finished.
                                wasPlayingBeforeInterruption = false
                                recoveryHandler.removeCallbacks(recoveryRunnable)
                            }
                        }
                        NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem, exo.isPlaying)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (wasPlayingBeforeInterruption && recoveryAttempts < 3) {
                            recoveryHandler.removeCallbacks(recoveryRunnable)
                            recoveryHandler.postDelayed(recoveryRunnable, 750L)
                        }
                        NowPlayingStore.publish(this@PlaybackService, exo.currentMediaItem, false)
                    }

                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                        if (item != null) recoveryAttempts = 0
                        NowPlayingStore.publish(this@PlaybackService, item, exo.isPlaying)
                    }
                })
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        recoveryHandler.removeCallbacksAndMessages(null)
        mediaSession?.release()
        player?.release()
        mediaSession = null
        player = null
        super.onDestroy()
    }
}
