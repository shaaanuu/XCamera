package dev.shaaanuu.xcamera.camera.cinema

import android.hardware.camera2.CameraCharacteristics
import android.util.Range

internal object CinemaCameraConfig {
    const val CAMERA_ID = "2"
    const val DEFAULT_LOOK_ID = 1

    // Preview
    const val PREVIEW_MIN_FPS = 30
    const val PREVIEW_FRAME_DURATION_NS = 33_366_666L

    // Cinema aspect ratio
    const val CINEMA_ASPECT_WIDTH = 3840
    const val CINEMA_ASPECT_HEIGHT = 1644
    const val ASPECT_TOLERANCE = 0.02f

    // Capture
    const val NOMINAL_FPS = 60

    // Recording
    const val VIDEO_WIDTH = 3840
    const val VIDEO_HEIGHT = 1644
    const val VIDEO_BITRATE = 90_000_000
    const val FRAME_DURATION_NS = 16_683_333L

    // Exposure defaults
    const val DEFAULT_ISO = 400
    const val DEFAULT_SHUTTER_ANGLE = 360f

    // Cinema Pro caps video ISO at 800 regardless of sensor range.
    const val ISO_800_CAP = 800

    // Shortest exposure the video pipeline accepts (2.8° @ 59.94 fps).
    const val VIDEO_MIN_SHUTTER_NS = 129_629L

    fun frameDurationFor(recording: Boolean): Long =
        if (recording) FRAME_DURATION_NS else maxOf(FRAME_DURATION_NS, PREVIEW_FRAME_DURATION_NS)

    fun pickFpsRange(characteristics: CameraCharacteristics, recording: Boolean): Range<Int> {
        val ranges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: error("Camera exposes no FPS ranges")

        if (recording) {
            return ranges.firstOrNull { it.lower == NOMINAL_FPS && it.upper == NOMINAL_FPS }
                ?: error("Camera does not expose [$NOMINAL_FPS, $NOMINAL_FPS]")
        }

        return ranges.firstOrNull { it.lower == PREVIEW_MIN_FPS && it.upper == NOMINAL_FPS }
            ?: ranges.firstOrNull { it.lower == NOMINAL_FPS && it.upper == NOMINAL_FPS }
            ?: error("Camera exposes neither [30, $NOMINAL_FPS] nor [$NOMINAL_FPS, $NOMINAL_FPS]")
    }
}
