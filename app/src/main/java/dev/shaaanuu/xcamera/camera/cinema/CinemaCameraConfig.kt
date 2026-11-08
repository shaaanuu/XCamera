package dev.shaaanuu.xcamera.camera.cinema

internal object CinemaCameraConfig {
    const val CAMERA_ID = "2"
    const val DEFAULT_LOOK_ID = 1

    // Preview
    const val PREVIEW_WIDTH = 2520
    const val PREVIEW_HEIGHT = 1080
    const val PREVIEW_MIN_FPS = 30
    const val PREVIEW_FRAME_DURATION_NS = 33_333_334L

    // Cinema aspect ratio, used for preview size selection
    const val CINEMA_ASPECT_WIDTH = 3840
    const val CINEMA_ASPECT_HEIGHT = 1644
    const val ASPECT_TOLERANCE = 0.02f

    // Capture defaults
    const val NOMINAL_FPS = 60
    const val DEFAULT_ISO = 400
    const val DEFAULT_EXPOSURE_NS = 16_683_333L

    // Recording configuration, retained for HlgEncoder
    const val VIDEO_WIDTH = 3840
    const val VIDEO_HEIGHT = 1644
    const val VIDEO_BITRATE = 90_000_000
    const val FRAME_DURATION_NS = 16_683_333L
}
