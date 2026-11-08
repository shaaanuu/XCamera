package dev.shaaanuu.xcamera.camera.cinema.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraConfig

internal class HlgEncoder {

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var drainThread: Thread? = null

    @Volatile private var running = false

    @Volatile
    var outputFormat: MediaFormat? = null
        private set

    @Volatile
    var failure: String? = null
        private set

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

                // Sony's original Cinema Pro-derived configuration.
                // No Main10 or Main fallback.
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

    private fun drainOutput(encoder: MediaCodec) {
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)

                when {
                    index >= 0 -> {
                        // Recording/muxing will consume these buffers
                        // in the next implementation stage.
                        encoder.releaseOutputBuffer(index, false)
                    }

                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = encoder.outputFormat
                    }
                }
            }
        } catch (error: Throwable) {
            if (running) {
                failure = "${error::class.simpleName}: ${error.message}"
                running = false
            }
        }
    }

    fun stop() {
        running = false

        try {
            drainThread?.join(1000)
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
