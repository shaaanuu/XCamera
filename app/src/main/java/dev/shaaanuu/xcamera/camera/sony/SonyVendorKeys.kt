package dev.shaaanuu.xcamera.camera.sony

import android.hardware.camera2.CaptureRequest

internal object SonyVendorKeys {

    // Cinema and exposure
    val cinemaProfile = byteKey("com.sonymobile.control.cinemaProfile")
    val aeMode = byteKey("com.sonymobile.control.aeMode")
    val exposureTimeHint = longKey("com.sonymobile.sensor.exposureTimeHint")
    val sensitivityHint = intKey("com.sonymobile.sensor.sensitivityHint")

    // Exposure and autofocus regions
    val aeRegionMode = byteKey("com.sonymobile.control.aeRegionMode")
    val afRegionMode = byteKey("com.sonymobile.control.afRegionMode")

    // White balance
    val awbColorCompensationAb = intKey("com.sonymobile.control.awbColorCompensationAb")
    val awbColorCompensationGm = intKey("com.sonymobile.control.awbColorCompensationGm")
    val wbMode = byteKey("com.sonymobile.control.wbMode")

    // Image processing
    val stillSkinSmoothLevel = intKey("com.sonymobile.control.stillSkinSmoothLevel")
    val stillHdrMode = byteKey("com.sonymobile.control.stillHdrMode")
    val fusionMode = byteKey("com.sonymobile.control.fusionMode")
    val vagueControlMode = byteKey("com.sonymobile.control.vagueControlMode")
    val powerSaveMode = byteKey("com.sonymobile.control.powerSaveMode")
    val videoStabilizationMode = byteKey("com.sonymobile.control.videoStabilizationMode")
    val exposureTimeLimit = longKey("com.sonymobile.control.exposureTimeLimit")

    // Logical camera
    val logicalMultiCameraMode = byteKey("com.sonymobile.logicalMultiCamera.mode")

    // Scene and condition detection
    val sceneDetectMode = byteKey("com.sonymobile.statistics.sceneDetectMode")
    val conditionDetectMode = byteKey("com.sonymobile.statistics.conditionDetectMode")

    // Face and object detection
    val faceSmileScoresMode = byteKey("com.sonymobile.statistics.faceSmileScoresMode")
    val faceSelectTrigger = byteKey("com.sonymobile.statistics.faceSelectTrigger")
    val objectSelectTrigger = byteKey("com.sonymobile.statistics.objectSelectTrigger")
    val faceSelectTriggerArea = intArrayKey("com.sonymobile.statistics.faceSelectTriggerArea")
    val objectSelectTriggerArea = intArrayKey("com.sonymobile.statistics.objectSelectTriggerArea")

    // Lens
    val focalLength = floatKey("com.sonymobile.lens.focalLength")

    private fun byteKey(name: String) = key(name, Byte::class.javaObjectType)

    private fun intKey(name: String) = key(name, Int::class.javaObjectType)

    private fun longKey(name: String) = key(name, Long::class.javaObjectType)

    private fun floatKey(name: String) = key(name, Float::class.javaObjectType)

    private fun intArrayKey(name: String) = key(name, IntArray::class.java)

    private fun <T : Any> key(name: String, type: Class<T>): CaptureRequest.Key<T>? {
        return try {
            CaptureRequest.Key(name, type)
        } catch (error: Throwable) {
            null
        }
    }
}
