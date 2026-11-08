package dev.shaaanuu.xcamera.camera.cinema

internal object CinemaCameraConfig {

    // Hardware
    const val CAMERA_ID = "2"

    // Sony Cinema Pro look
    const val DEFAULT_LOOK_ID = 1 // VENICE Look 1

    // Recording
    const val VIDEO_WIDTH = 3840
    const val VIDEO_HEIGHT = 1644
    const val VIDEO_BITRATE = 90_000_000

    // 59.94 FPS (60000/1001)
    const val NOMINAL_FPS = 60
    const val FRAME_DURATION_NS = 16_683_333L
}
