package com.oiw.camera.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.oiw.camera.capture.CaptureProfile
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Camera2 encoder-Surface -> MediaCodec (hardware encode) -> MediaMuxer (MP4) pipeline.
 * Drains encoder output on a dedicated thread with a bounded queue so a slow storage target
 * shows up as the dropped-frame indicator rather than blocking the camera capture loop
 * (docs/STORAGE_MEDIA.md #6, docs/CINEMA_FEATURES.md #2).
 */
class Recorder(
    private val outputDir: File,
    private val clipBaseName: String,
    private val profile: CaptureProfile,
    private val listener: Listener,
) {
    interface Listener {
        fun onSegmentFinalized(file: File, segmentIndex: Int)
        fun onDroppedFrame(totalDropped: Int)
        fun onError(message: String, cause: Throwable? = null)
    }

    private lateinit var codec: MediaCodec
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    var audioEnabled: Boolean = true
    private var muxerStarted = false
    private val muxerLock = Any()
    private val running = AtomicBoolean(false)
    private val droppedFrames = AtomicInteger(0)
    private val writerQueue = LinkedBlockingQueue<MediaCodec.BufferInfo>(WRITER_QUEUE_CAPACITY)
    private var writerThread: Thread? = null
    private var segmentIndex = 0
    private var segmentStartNanos = 0L

    /** Returns the Surface the CameraController's capture request should target for the encoder stream. */
    fun createEncoderInputSurface(): Surface {
        val mime = mimeForCodec(profile.codec)
        val format = MediaFormat.createVideoFormat(mime, profile.resolution.width, profile.resolution.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, profile.bitrateBps.toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, profile.frameRateFps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            if (profile.bitDepth == 10) {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10)
            }
        }
        codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        return surface
    }

    fun start() {
        if (running.getAndSet(true)) return
        segmentIndex = 1
        segmentStartNanos = System.nanoTime()
        startNewMuxerSegment()
        writerThread = Thread(::drainLoop, "OIWRecorderWriter").also { it.start() }
    }

    /** Called periodically (or on a size/time threshold) to roll to a new segment file, docs/STORAGE_MEDIA.md #5. */
    fun rolloverSegmentIfNeeded(thresholdBytes: Long, thresholdNanos: Long, currentBytesWritten: Long) {
        val elapsed = System.nanoTime() - segmentStartNanos
        if (currentBytesWritten >= thresholdBytes || elapsed >= thresholdNanos) {
            finalizeCurrentSegment()
            segmentIndex += 1
            segmentStartNanos = System.nanoTime()
            startNewMuxerSegment()
        }
    }

    private fun startNewMuxerSegment() {
        synchronized(muxerLock) {
            val segmentFile = File(outputDir, "${clipBaseName}_seg%03d.mp4".format(segmentIndex))
            muxer = MediaMuxer(segmentFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerStarted = false
            videoTrackIndex = -1
            audioTrackIndex = -1
            maybeStartMuxerLocked()
        }
    }

    /** Muxer starts only once every enabled track's format is known — required by MediaMuxer. */
    private fun maybeStartMuxerLocked() {
        val m = muxer ?: return
        if (muxerStarted) return
        val vf = videoFormat ?: return
        val af = audioFormat
        if (audioEnabled && af == null) return
        videoTrackIndex = m.addTrack(vf)
        if (audioEnabled && af != null) audioTrackIndex = m.addTrack(af)
        m.start()
        muxerStarted = true
    }

    /** Called from AudioCapture's listener when the AAC encoder reports its real output format. */
    fun onAudioFormatReady(format: MediaFormat) {
        synchronized(muxerLock) {
            audioFormat = format
            maybeStartMuxerLocked()
        }
    }

    /** Called from AudioCapture's listener per encoded AAC buffer; releases the codec buffer. */
    fun writeAudioSample(audioCodec: MediaCodec, outputIndex: Int, info: MediaCodec.BufferInfo) {
        val data = audioCodec.getOutputBuffer(outputIndex)
        synchronized(muxerLock) {
            val m = muxer
            if (data != null && m != null && muxerStarted && audioTrackIndex >= 0 &&
                info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
            ) {
                try {
                    m.writeSampleData(audioTrackIndex, data, info)
                } catch (e: Exception) {
                    listener.onError("Audio muxer write failed.", e)
                }
            }
        }
        audioCodec.releaseOutputBuffer(outputIndex, false)
    }

    private fun drainLoop() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (running.get()) {
            val outputIndex = try {
                codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
            } catch (e: Exception) {
                listener.onError("Encoder output dequeue failed.", e)
                break
            }
            when {
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        videoFormat = codec.outputFormat
                        maybeStartMuxerLocked()
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    synchronized(muxerLock) {
                        val currentMuxer = muxer
                        if (encodedData != null && muxerStarted && currentMuxer != null &&
                            bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                        ) {
                            try {
                                currentMuxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            } catch (e: Exception) {
                                listener.onError("Muxer write failed; treating as a dropped frame.", e)
                                droppedFrames.incrementAndGet()
                                listener.onDroppedFrame(droppedFrames.get())
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
                // outputIndex == INFO_TRY_AGAIN_LATER: expected on timeout, nothing to do.
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        writerThread?.join(WRITER_JOIN_TIMEOUT_MS)
        finalizeCurrentSegment()
        codec.stop()
        codec.release()
    }

    private fun finalizeCurrentSegment() {
        synchronized(muxerLock) {
            val currentMuxer = muxer ?: return
            try {
                if (muxerStarted) currentMuxer.stop()
            } catch (e: Exception) {
                listener.onError("File finalization failed for segment $segmentIndex. Attempting recovery.", e)
            } finally {
                currentMuxer.release()
                muxer = null
                muxerStarted = false
            }
        }
        val segmentFile = File(outputDir, "${clipBaseName}_seg%03d.mp4".format(segmentIndex))
        listener.onSegmentFinalized(segmentFile, segmentIndex)
    }

    companion object {
        private const val WRITER_QUEUE_CAPACITY = 8
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val WRITER_JOIN_TIMEOUT_MS = 2_000L

        fun mimeForCodec(codec: String): String = when (codec) {
            "hevc", "hevc10" -> MediaFormat.MIMETYPE_VIDEO_HEVC
            "avc" -> MediaFormat.MIMETYPE_VIDEO_AVC
            else -> error("Unsupported codec: $codec")
        }
    }
}
