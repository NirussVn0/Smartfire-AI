package com.example.service.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private data class AlertSessionConfig(
        val alertMode: AlertMode,
        val soundType: AlarmSoundType,
        val volumePercent: Int,
        val isLevel1Warning: Boolean
    )

    private val stateLock = Any()
    private val audioHardwareMutex = Mutex()
    private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sessionSeq: Long = 0L
    private var activeToken: Long = 0L
    private var activeSessionId: String? = null
    private var activeConfig: AlertSessionConfig? = null
    private var activeManagedJob: Job? = null
    private var activeRunJob: Job? = null

    private fun isTokenCurrent(token: Long): Boolean = synchronized(stateLock) {
        activeToken == token
    }

    fun startManagedAlert(
        context: Context,
        sessionId: String,
        alertMode: AlertMode,
        soundType: AlarmSoundType,
        volumePercent: Int,
        isLevel1Warning: Boolean
    ) {
        if (alertMode == AlertMode.SILENT) {
            stopManagedAlert(null)
            return
        }

        val appContext = context.applicationContext
        val safeVol = volumePercent.coerceIn(10, 100)
        val requestedConfig = AlertSessionConfig(alertMode, soundType, safeVol, isLevel1Warning)

        var prevManaged: Job? = null
        var prevInline: Job? = null
        var myToken = 0L

        synchronized(stateLock) {
            if (activeManagedJob?.isActive == true &&
                activeSessionId == sessionId &&
                activeConfig == requestedConfig
            ) {
                return
            }

            myToken = ++sessionSeq
            activeToken = myToken
            activeSessionId = sessionId
            activeConfig = requestedConfig
            prevManaged = activeManagedJob
            prevInline = activeRunJob
            activeRunJob = null

            activeManagedJob = playerScope.launch {
                try {
                    audioHardwareMutex.withLock {
                        if (!isTokenCurrent(myToken)) return@withLock
                        runLoopInternal(
                            context = appContext,
                            alertMode = alertMode,
                            soundType = soundType,
                            volumePercent = safeVol,
                            isLevel1Warning = isLevel1Warning
                        )
                    }
                } finally {
                    synchronized(stateLock) {
                        if (activeToken == myToken) {
                            activeManagedJob = null
                            activeSessionId = null
                            activeConfig = null
                        }
                    }
                }
            }
        }

        prevManaged?.cancel()
        prevInline?.cancel()
    }

    fun stopManagedAlert(sessionId: String? = null) {
        var managedToCancel: Job? = null
        var inlineToCancel: Job? = null

        synchronized(stateLock) {
            if (sessionId == null || sessionId == activeSessionId) {
                ++sessionSeq
                activeToken = sessionSeq
                activeSessionId = null
                activeConfig = null
                managedToCancel = activeManagedJob
                inlineToCancel = activeRunJob
                activeManagedJob = null
                activeRunJob = null
            }
        }

        managedToCancel?.cancel()
        inlineToCancel?.cancel()
    }

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

    private fun createFallbackToneGenerator(isLevel1Warning: Boolean, safeVol: Int): ToneGenerator? {
        return try {
            val tgStream = if (isLevel1Warning) AudioManager.STREAM_NOTIFICATION else AudioManager.STREAM_ALARM
            ToneGenerator(tgStream, safeVol)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun runAlertLoop(
        context: Context,
        alertMode: AlertMode,
        soundType: AlarmSoundType,
        volumePercent: Int,
        isLevel1Warning: Boolean
    ) {
        if (alertMode == AlertMode.SILENT) {
            stopManagedAlert(null)
            return
        }

        val appContext = context.applicationContext
        val safeVol = volumePercent.coerceIn(10, 100)
        val parentJob = currentCoroutineContext()[Job]
        val sessionJob = Job(parentJob)

        var prevManaged: Job? = null
        var prevInline: Job? = null
        var myToken = 0L

        synchronized(stateLock) {
            myToken = ++sessionSeq
            activeToken = myToken
            activeSessionId = "inline_$myToken"
            activeConfig = AlertSessionConfig(alertMode, soundType, safeVol, isLevel1Warning)
            prevManaged = activeManagedJob
            prevInline = activeRunJob
            activeManagedJob = null
            activeRunJob = sessionJob
        }

        prevManaged?.cancel()
        prevInline?.cancel()

        try {
            withContext(sessionJob + Dispatchers.Default) {
                audioHardwareMutex.withLock {
                    if (!isTokenCurrent(myToken)) return@withLock
                    runLoopInternal(
                        context = appContext,
                        alertMode = alertMode,
                        soundType = soundType,
                        volumePercent = safeVol,
                        isLevel1Warning = isLevel1Warning
                    )
                }
            }
        } catch (e: CancellationException) {
            if (parentJob?.isActive == false) {
                throw e
            }
        } finally {
            sessionJob.complete()
            synchronized(stateLock) {
                if (activeToken == myToken) {
                    activeRunJob = null
                    activeSessionId = null
                    activeConfig = null
                }
            }
        }
    }

    private suspend fun runLoopInternal(
        context: Context,
        alertMode: AlertMode,
        soundType: AlarmSoundType,
        volumePercent: Int,
        isLevel1Warning: Boolean
    ) = withContext(Dispatchers.Default) {
        var audioTrack: AudioTrack? = null
        var ringtone: Ringtone? = null
        var toneGen: ToneGenerator? = null
        var pcmChunk: ShortArray? = null
        var writeOffset = 0

        val audioManager = try {
            context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        } catch (_: Throwable) {
            null
        }
        var focusRequest: AudioFocusRequest? = null
        var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

        try {
            val playSound = alertMode == AlertMode.SOUND_AND_VIBRATE
            val safeVol = volumePercent.coerceIn(10, 100)
            val volFraction = safeVol / 100f

            if (playSound) {
                // Request transient ducking AudioFocus cleanly across API levels
                if (audioManager != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val focusAttrs = AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                                .setAudioAttributes(focusAttrs)
                                .setOnAudioFocusChangeListener { }
                                .build()
                            audioManager.requestAudioFocus(req)
                            focusRequest = req
                        } else {
                            val listener = AudioManager.OnAudioFocusChangeListener { }
                            @Suppress("DEPRECATION")
                            audioManager.requestAudioFocus(
                                listener,
                                AudioManager.STREAM_ALARM,
                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                            )
                            legacyFocusListener = listener
                        }
                    } catch (_: Throwable) {}
                }

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
                        ringtone = try {
                            RingtoneManager.getRingtone(context, uri)?.apply {
                                audioAttributes = AudioAttributes.Builder()
                                    .setUsage(if (isLevel1Warning) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    isLooping = !isLevel1Warning
                                    volume = volFraction
                                }
                                play()
                            }
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    if (ringtone == null) {
                        toneGen = createFallbackToneGenerator(isLevel1Warning, safeVol)
                    }
                } else {
                    val sampleRate = 22050
                    val chunk = buildPcmToneBuffer(soundType, isLevel1Warning, 1.0f, sampleRate)
                    pcmChunk = chunk
                    val minBuf = AudioTrack.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    if (minBuf > 0) {
                        val bufferSizeInBytes = maxOf(minBuf * 2, chunk.size * 2)
                        val attrs = AudioAttributes.Builder()
                            .setUsage(if (isLevel1Warning) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        val format = AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()

                        var createdTrack: AudioTrack? = null
                        try {
                            createdTrack = AudioTrack.Builder()
                                .setAudioAttributes(attrs)
                                .setAudioFormat(format)
                                .setBufferSizeInBytes(bufferSizeInBytes)
                                .setTransferMode(AudioTrack.MODE_STREAM)
                                .build()

                            if (createdTrack.state == AudioTrack.STATE_INITIALIZED) {
                                createdTrack.setVolume(volFraction)
                                val written = createdTrack.write(chunk, 0, chunk.size, AudioTrack.WRITE_NON_BLOCKING)
                                if (written >= 0) {
                                    writeOffset = written % chunk.size
                                    createdTrack.play()
                                    audioTrack = createdTrack
                                } else {
                                    try { createdTrack.release() } catch (_: Throwable) {}
                                }
                            } else {
                                try { createdTrack.release() } catch (_: Throwable) {}
                            }
                        } catch (_: Throwable) {
                            try { createdTrack?.release() } catch (_: Throwable) {}
                            audioTrack = null
                        }
                    }

                    // Allocate fallback ToneGenerator ONLY when AudioTrack failed to initialize/start
                    if (audioTrack == null) {
                        toneGen = createFallbackToneGenerator(isLevel1Warning, safeVol)
                    }
                }
            }

            val vibDuration = if (isLevel1Warning) 220L else 380L
            val vibAmp = if (isLevel1Warning) 160 else 255
            val vibIntervalMs = if (isLevel1Warning) 1200L else 600L
            var lastVibAtMs = 0L
            var lastToneGenAtMs = 0L

            while (isActive) {
                val now = System.currentTimeMillis()
                if (now - lastVibAtMs >= vibIntervalMs) {
                    triggerVibrationPulse(context, vibDuration, vibAmp)
                    lastVibAtMs = now
                }

                if (playSound) {
                    val activeTrack = audioTrack
                    val activeChunk = pcmChunk
                    if (activeTrack != null && activeChunk != null) {
                        var streamFailed = false
                        while (isActive) {
                            val toWrite = activeChunk.size - writeOffset
                            val written = try {
                                activeTrack.write(activeChunk, writeOffset, toWrite, AudioTrack.WRITE_NON_BLOCKING)
                            } catch (_: Throwable) {
                                -1
                            }
                            if (written < 0) {
                                streamFailed = true
                                break
                            } else if (written == 0) {
                                break
                            } else {
                                writeOffset = (writeOffset + written) % activeChunk.size
                                if (written < toWrite) break
                            }
                        }
                        if (streamFailed) {
                            try { activeTrack.stop() } catch (_: Throwable) {}
                            try { activeTrack.release() } catch (_: Throwable) {}
                            audioTrack = null
                            if (toneGen == null) {
                                toneGen = createFallbackToneGenerator(isLevel1Warning, safeVol)
                            }
                        }
                    } else if (ringtone != null) {
                        try {
                            if (!ringtone.isPlaying) {
                                ringtone.play()
                            }
                        } catch (_: Throwable) {}
                    } else if (toneGen != null && now - lastToneGenAtMs >= vibIntervalMs) {
                        try {
                            val tone = if (isLevel1Warning) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                            toneGen?.startTone(tone, 350)
                        } catch (_: Throwable) {}
                        lastToneGenAtMs = now
                    }
                }

                val stepDelayMs = if (playSound && audioTrack != null) 100L else vibIntervalMs
                delay(stepDelayMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Ignore audio hardware failures on headless test environments
        } finally {
            try { ringtone?.stop() } catch (_: Throwable) {}
            try {
                audioTrack?.let { track ->
                    try { track.pause() } catch (_: Throwable) {}
                    try { track.flush() } catch (_: Throwable) {}
                    try { track.stop() } catch (_: Throwable) {}
                    try { track.release() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
            try { toneGen?.stopTone(); toneGen?.release() } catch (_: Throwable) {}

            if (audioManager != null) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                    } else {
                        legacyFocusListener?.let {
                            @Suppress("DEPRECATION")
                            audioManager.abandonAudioFocus(it)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }
}
