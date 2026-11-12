package dev.shaaanuu.xcamera.camera.cinema.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraConfig
import java.io.FileDescriptor

internal class HlgEncoder {

    companion object {
        private const val TAG = "XCamera-HlgEncoder"
    }

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var drainThread: Thread? = null

    @Volatile private var running = false
    @Volatile private var recording = false
    @Volatile private var sampleCount = 0

    private val muxerLock = Any()
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var trackIndex = -1
    private var firstPts = -1L
    private var pendingFd: FileDescriptor? = null

    @Volatile
    var outputFormat: MediaFormat? = null
        private set

    @Volatile
    var failure: String? = null
        private set

    val samplesWritten: Int
        get() = sampleCount

    fun start(): Surface {
        check(codec == null) { "Encoder already started" }

        val encoderInfo = selectEncoder()
        val encoder = MediaCodec.createByCodecName(encoderInfo.name)

        var surface: Surface? = null

        try {
            val format = createFormat()
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()

            codec = encoder
            inputSurface = surface
            running = true

            drainThread = Thread({ drainOutput(encoder) }, "XCamera-HLG-Drain").apply { start() }

            return surface
        } catch (error: Throwable) {
            surface?.release()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            throw IllegalStateException(
                "Failed to initialize HEVC Main10 HDR10 encoder: ${error.message}",
                error,
            )
        }
    }

    fun beginRecording(fd: FileDescriptor) {
        synchronized(muxerLock) {
            pendingFd = fd
            recording = true
            sampleCount = 0
            firstPts = -1L
            if (outputFormat != null) openMuxerLocked()
        }
    }

    fun stopRecording() {
        synchronized(muxerLock) {
            recording = false
            try {
                muxer?.let {
                    if (muxerStarted) runCatching { it.stop() }
                    it.release()
                }
            } catch (error: Throwable) {
                Log.e(TAG, "Muxer release failed", error)
            }
            muxer = null
            muxerStarted = false
            trackIndex = -1
            firstPts = -1L
            pendingFd = null
        }
    }

    private fun openMuxerLocked() {
        if (muxer != null) return
        val fd = pendingFd ?: return
        val format = outputFormat ?: return

        try {
            val m = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            trackIndex = m.addTrack(format)
            m.start()
            muxer = m
            muxerStarted = true
            firstPts = -1L
        } catch (error: Throwable) {
            Log.e(TAG, "Muxer open failed", error)
            pendingFd = null
            recording = false
        }
    }

    private fun drainOutput(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    index >= 0 -> {
                        try {
                            handleOutputBuffer(encoder, index, bufferInfo)
                        } finally {
                            encoder.releaseOutputBuffer(index, false)
                        }
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                        synchronized(muxerLock) {
                            if (recording && muxer == null) openMuxerLocked()
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            if (running) {
                failure = "${error::class.simpleName}: ${error.message}"
                running = false
                Log.e(TAG, "Drain loop failed", error)
            }
        }
    }

    private fun handleOutputBuffer(
        encoder: MediaCodec,
        index: Int,
        bufferInfo: MediaCodec.BufferInfo,
    ) {
        if (bufferInfo.size <= 0) return
        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) return

        synchronized(muxerLock) {
            if (recording && muxer == null && outputFormat != null) openMuxerLocked()
            val m = muxer ?: return
            if (!muxerStarted) return

            val buffer = encoder.getOutputBuffer(index) ?: return
            buffer.position(bufferInfo.offset)
            buffer.limit(bufferInfo.offset + bufferInfo.size)

            if (firstPts < 0L) firstPts = bufferInfo.presentationTimeUs

            val adjusted =
                MediaCodec.BufferInfo().apply {
                    set(
                        bufferInfo.offset,
                        bufferInfo.size,
                        bufferInfo.presentationTimeUs - firstPts,
                        bufferInfo.flags,
                    )
                }

            try {
                m.writeSampleData(trackIndex, buffer, adjusted)
                sampleCount++
            } catch (error: Throwable) {
                Log.e(TAG, "writeSampleData failed", error)
            }
        }
    }

    private fun selectEncoder(): MediaCodecInfo {
        val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos

        for (info in codecs) {
            if (!info.isEncoder) continue
            if (!info.isHardwareAccelerated) continue
            if (info.isSoftwareOnly) continue

            val hevcType =
                info.supportedTypes.firstOrNull {
                    it.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)
                } ?: continue

            val capabilities =
                runCatching { info.getCapabilitiesForType(hevcType) }.getOrNull() ?: continue

            val hasRequiredProfile =
                capabilities.profileLevels.any {
                    it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                }

            if (!hasRequiredProfile) continue

            val hasSurfaceInput =
                capabilities.colorFormats.contains(
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )

            if (!hasSurfaceInput) continue

            val video = capabilities.videoCapabilities ?: continue

            if (
                !video.areSizeAndRateSupported(
                    CinemaCameraConfig.VIDEO_WIDTH,
                    CinemaCameraConfig.VIDEO_HEIGHT,
                    CinemaCameraConfig.NOMINAL_FPS.toDouble(),
                )
            )
                continue

            if (!video.bitrateRange.contains(CinemaCameraConfig.VIDEO_BITRATE)) continue

            return info
        }

        error(
            "No hardware HEVC encoder supports Main10 HDR10 profile 4096 " +
                "at the required resolution and frame rate."
        )
    }

    private fun createFormat(): MediaFormat {
        return MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_HEVC,
                CinemaCameraConfig.VIDEO_WIDTH,
                CinemaCameraConfig.VIDEO_HEIGHT,
            )
            .apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
                )
                setInteger(MediaFormat.KEY_BIT_RATE, CinemaCameraConfig.VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, CinemaCameraConfig.NOMINAL_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                )
                setInteger(
                    MediaFormat.KEY_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                )
                setInteger(MediaFormat.KEY_LEVEL, 1)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020)
                setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            }
    }

    fun stop() {
        synchronized(muxerLock) {
            recording = false
            try {
                muxer?.let {
                    if (muxerStarted) runCatching { it.stop() }
                    it.release()
                }
            } catch (_: Throwable) {}
            muxer = null
            muxerStarted = false
            pendingFd = null
        }

        running = false

        try {
            drainThread?.join(200)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        drainThread = null

        codec?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        codec = null

        inputSurface?.release()
        inputSurface = null
    }
}
