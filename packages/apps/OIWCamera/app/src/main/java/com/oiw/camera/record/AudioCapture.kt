package com.oiw.camera.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PCM capture -> AAC encode, feeding the shared muxer via [Recorder]. Also computes live
 * peak/RMS meters and a clip counter for the audio panel (docs/CINEMA_FEATURES.md #2).
 * Bluetooth is never offered as a capture source (assumptions.md C-4); source selection between
 * internal mic and USB audio happens at the AudioRecord routing level via the system's
 * setPreferredDevice on the record instance (caller passes an AudioDeviceInfo id if chosen).
 */
class AudioCapture(
    private val sampleRate: Int = 48_000,
    private val channelCount: Int = 2,
    private val listener: Listener,
) {
    interface Listener {
        fun onMeters(peakDbfs: Double, rmsDbfs: Double, clippedSamples: Int)
        fun onEncodedAudio(codec: MediaCodec, outputIndex: Int, info: MediaCodec.BufferInfo)
        fun onAudioFormatReady(format: MediaFormat)
        fun onError(message: String, cause: Throwable? = null)
    }

    private var audioRecord: AudioRecord? = null
    private var codec: MediaCodec? = null
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO checked by CameraActivity before start.
    fun start(preferredDeviceId: Int? = null) {
        if (running.getAndSet(true)) return
        val channelMask = if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            listener.onError("AudioRecord.getMinBufferSize failed ($minBuf) at ${sampleRate}Hz — source unavailable.")
            running.set(false)
            return
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER, sampleRate, channelMask,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 4,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            listener.onError("AudioRecord failed to initialize (state=${record.state}).")
            running.set(false)
            return
        }
        audioRecord = record

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 256_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuf * 4)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec = enc
        enc.start()
        record.startRecording()

        thread = Thread(::loop, "OIWAudioCapture").also { it.start() }
    }

    private fun loop() {
        val record = audioRecord ?: return
        val enc = codec ?: return
        val pcm = ShortArray(4096)
        val info = MediaCodec.BufferInfo()
        while (running.get()) {
            val read = record.read(pcm, 0, pcm.size)
            if (read <= 0) continue

            var peak = 0; var sumSq = 0.0; var clipped = 0
            for (i in 0 until read) {
                val v = abs(pcm[i].toInt())
                if (v > peak) peak = v
                if (v >= 32767) clipped++
                sumSq += v.toDouble() * v
            }
            val peakDb = 20 * Math.log10(peak.coerceAtLeast(1) / 32768.0)
            val rmsDb = 20 * Math.log10(sqrt(sumSq / read).coerceAtLeast(1.0) / 32768.0)
            listener.onMeters(peakDb, rmsDb, clipped)

            val inIndex = enc.dequeueInputBuffer(10_000)
            if (inIndex >= 0) {
                val buf = enc.getInputBuffer(inIndex) ?: continue
                buf.clear()
                buf.asShortBuffer().put(pcm, 0, read)
                enc.queueInputBuffer(inIndex, 0, read * 2, System.nanoTime() / 1000, 0)
            }
            var outIndex = enc.dequeueOutputBuffer(info, 0)
            while (outIndex >= 0 || outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    listener.onAudioFormatReady(enc.outputFormat)
                } else {
                    listener.onEncodedAudio(enc, outIndex, info)
                }
                outIndex = enc.dequeueOutputBuffer(info, 0)
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        thread?.join(1_000)
        runCatching { audioRecord?.stop() }
        audioRecord?.release(); audioRecord = null
        runCatching { codec?.stop() }
        codec?.release(); codec = null
    }
}
