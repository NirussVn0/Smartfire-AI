package com.example.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.domain.model.AlarmSoundType
import com.example.domain.model.AlertMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Reliable, customizable alert audio & vibration engine supporting:
 * - AlertMode: SOUND_AND_VIBRATE, VIBRATE_ONLY, SILENT
 * - AlarmSoundType: FIRE_SIREN, URGENT_BEEP, SYSTEM_ALARM, SYSTEM_NOTIFICATION
 * - Volume percentage (10..100%)
 * - Level 1 (gentler early warning) vs Level 2 (urgent continuous fire siren)
 */
object AlarmAudioPlayer {

    fun applyTargetVolume(context: Context, volumePercent: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val fraction = (volumePercent.coerceIn(10, 100) / 100f)
            val streams = listOf(
                AudioManager.STREAM_ALARM,
                AudioManager.STREAM_MUSIC
            )
            for (stream in streams) {
                try {
                    val maxVol = am.getStreamMaxVolume(stream)
                    if (maxVol > 0) {
                        val targetVol = (maxVol * fraction).roundToInt().coerceIn(1, maxVol)
                        am.setStreamVolume(stream, targetVol, 0)
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun triggerVibrationPulse(context: Context, durationMs: Long, amplitude: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (_: Throwable) {}
    }

    private fun buildPcmToneBuffer(
        soundType: AlarmSoundType,
        isLevel1Warning: Boolean,
        amplitudeScale: Float,
        sampleRate: Int = 22050
    ): ShortArray {
        val totalSamples = sampleRate // 1.0s cycle
        val buffer = ShortArray(totalSamples)
        val twoPi = 2.0 * Math.PI
        val scale = amplitudeScale.coerceIn(0.08f, 1.0f)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val sampleVal = when (soundType) {
                AlarmSoundType.FIRE_SIREN -> {
                    val sweepRate = if (isLevel1Warning) 1.5 else 3.0
                    val phase = (t * sweepRate) % 1.0
                    val freq = if (isLevel1Warning) {
                        if (phase < 0.4) 980.0 else 0.0 // intermittent Level 1 warning
                    } else {
                        if (phase < 0.5) 1620.0 else 1080.0
                    }
                    if (freq <= 0.0) {
                        0.0
                    } else {
                        val s1 = sin(twoPi * freq * t)
                        val s2 = sin(twoPi * freq * 2.0 * t)
                        (if (s1 >= 0.0) 0.72 else -0.72) + (if (s2 >= 0.0) 0.24 else -0.24)
                    }
                }
                AlarmSoundType.URGENT_BEEP -> {
                    val phase = (t * (if (isLevel1Warning) 2.0 else 4.0)) % 1.0
                    if (phase < 0.45) {
                        val freq = if (isLevel1Warning) 1250.0 else 1950.0
                        sin(twoPi * freq * t) * 0.90
                    } else {
                        0.0
                    }
                }
                AlarmSoundType.SYSTEM_ALARM,
                AlarmSoundType.SYSTEM_NOTIFICATION -> 0.0
            }
            buffer[i] = (sampleVal * scale * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return buffer
    }

    suspend fun runAlertLoop(
        context: Context,
        alertMode: AlertMode,
        soundType: AlarmSoundType,
        volumePercent: Int,
        isLevel1Warning: Boolean
    ) = coroutineScope {
        if (alertMode == AlertMode.SILENT) return@coroutineScope

        withContext(Dispatchers.Default) {
            var audioTrack: AudioTrack? = null
            var ringtone: Ringtone? = null
            var toneGen: ToneGenerator? = null

            try {
                val playSound = alertMode == AlertMode.SOUND_AND_VIBRATE
                val safeVol = volumePercent.coerceIn(10, 100)
                val volFraction = safeVol / 100f

                if (playSound) {
                    applyTargetVolume(context, safeVol)

                    if (soundType == AlarmSoundType.SYSTEM_ALARM || soundType == AlarmSoundType.SYSTEM_NOTIFICATION) {
                        val uri = if (soundType == AlarmSoundType.SYSTEM_ALARM) {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        } else {
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        }
                        if (uri != null) {
                            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    isLooping = !isLevel1Warning
                                    volume = volFraction
                                }
                                audioAttributes = AudioAttributes.Builder()
                                    .setUsage(if (isLevel1Warning) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                                play()
                            }
                        }
                    } else {
                        val sampleRate = 22050
                        val pcm = buildPcmToneBuffer(soundType, isLevel1Warning, volFraction, sampleRate)
                        val attrs = AudioAttributes.Builder()
                            .setUsage(if (isLevel1Warning) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        val format = AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()

                        audioTrack = AudioTrack.Builder()
                            .setAudioAttributes(attrs)
                            .setAudioFormat(format)
                            .setBufferSizeInBytes(pcm.size * 2)
                            .setTransferMode(AudioTrack.MODE_STATIC)
                            .build()
                        audioTrack.write(pcm, 0, pcm.size)
                        audioTrack.setLoopPoints(0, pcm.size, -1)
                        audioTrack.setVolume(volFraction)
                        audioTrack.play()
                    }

                    // Backup ToneGenerator in case AudioTrack/Ringtone fails on restricted devices
                    try {
                        val tgStream = if (isLevel1Warning) AudioManager.STREAM_NOTIFICATION else AudioManager.STREAM_ALARM
                        toneGen = ToneGenerator(tgStream, safeVol)
                    } catch (_: Throwable) {}
                }

                val vibDuration = if (isLevel1Warning) 220L else 380L
                val vibAmp = if (isLevel1Warning) 160 else 255
                val loopDelay = if (isLevel1Warning) 1200L else 600L

                while (isActive) {
                    triggerVibrationPulse(context, vibDuration, vibAmp)
                    if (playSound) {
                        if (ringtone != null && !ringtone.isPlaying) {
                            try { ringtone.play() } catch (_: Throwable) {}
                        }
                        if (audioTrack == null && ringtone == null) {
                            try {
                                val tone = if (isLevel1Warning) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                                toneGen?.startTone(tone, 350)
                            } catch (_: Throwable) {}
                        }
                    }
                    delay(loopDelay)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Ignore audio hardware failures on headless test environments
            } finally {
                try { ringtone?.stop() } catch (_: Throwable) {}
                try { audioTrack?.stop(); audioTrack?.release() } catch (_: Throwable) {}
                try { toneGen?.stopTone(); toneGen?.release() } catch (_: Throwable) {}
            }
        }
    }
}
