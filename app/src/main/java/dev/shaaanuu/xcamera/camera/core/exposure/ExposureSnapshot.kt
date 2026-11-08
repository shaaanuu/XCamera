package dev.shaaanuu.xcamera.camera.core.exposure

internal data class ExposureSnapshot(
    val isoLabel: String,
    val shutterLabel: String,
    val isoManual: Boolean,
    val shutterManual: Boolean,
    val isoPending: Boolean,
    val shutterPending: Boolean,
)
