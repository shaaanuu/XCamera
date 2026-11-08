package dev.shaaanuu.xcamera.camera.cinema

internal object CinemaCameraConfig {
    const val CAMERA_ID = "2"
    const val DEFAULT_LOOK_ID = 1

    // Preview
    const val PREVIEW_WIDTH = 2520
    const val PREVIEW_HEIGHT = 1080
    const val PREVIEW_FRAME_DURATION_NS = 16_683_333L

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
}
