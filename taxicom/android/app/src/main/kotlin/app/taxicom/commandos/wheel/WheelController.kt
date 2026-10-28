package app.taxicom.commandos.wheel

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import app.taxicom.commandos.decision.Action
import app.taxicom.commandos.decision.DecidedBy
import app.taxicom.commandos.decision.OfferPipeline

/**
 * Claims the AVRCP slot so Toyota Prius / Corolla steering-wheel Seek and Play buttons reach us
 * (docs/steering-wheel-control.md). NEXT = accept, PREVIOUS = decline/ignore, PLAY = repeat / long-press stops auto.
 */
class WheelController(
    private val context: Context,
    private val pipeline: OfferPipeline,
) {
    private var session: MediaSessionCompat? = null
    private var focusRequest: AudioFocusRequest? = null
    private var playDownAt = 0L
    private var enabled = true

    fun setEnabled(on: Boolean) {
        enabled = on
        if (!on) stop()
    }

    fun start() {
        if (session != null) return
        val s = MediaSessionCompat(context, "TaxiCommandosWheel").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS,
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onMediaButtonEvent(intent: Intent?): Boolean {
                    // MediaButtonReceiver already routes to the transport callbacks below.
                    return super.onMediaButtonEvent(intent)
                }

                override fun onSkipToNext() {
                    if (!enabled) return
                    pipeline.decide(Action.ACCEPT, DecidedBy.WHEEL)
                }

                override fun onSkipToPrevious() {
                    if (!enabled) return
                    if (pipeline.veto()) return
                    pipeline.decide(Action.DECLINE, DecidedBy.WHEEL)
                }

                override fun onPlay() {
                    if (!enabled) return
                    val held = playDownAt > 0 && SystemClock.elapsedRealtime() - playDownAt > 700
                    playDownAt = 0
                    if (held) {
                        pipeline.veto()
                    } else {
                        pipeline.repeatLast()
                    }
                }

                override fun onPause() {
                    playDownAt = SystemClock.elapsedRealtime()
                }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackStateCompat.ACTION_PLAY_PAUSE,
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                    .build(),
            )
            setMetadata(
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Taxi Commandos")
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "On shift")
                    .build(),
            )
            isActive = true
        }
        session = s
        requestFocus()
        MediaButtonReceiver.handleIntent(s, Intent(Intent.ACTION_MEDIA_BUTTON))
    }

    fun updateNowPlaying(title: String, artist: String) {
        session?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .build(),
        )
        session?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1f)
                .build(),
        )
    }

    fun stop() {
        abandonFocus()
        session?.isActive = false
        session?.release()
        session = null
    }

    private fun requestFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener { }
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonFocus() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }
}
