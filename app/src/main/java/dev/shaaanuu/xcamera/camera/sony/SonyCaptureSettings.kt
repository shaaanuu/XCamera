package dev.shaaanuu.xcamera.camera.sony

import android.hardware.camera2.CaptureRequest

internal object SonyCaptureSettings {

    fun applyBasePipeline(
        builder: CaptureRequest.Builder,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        focalLengthMm: Float,
    ) {
        val keys = SonyVendorKeys

        keys.aeRegionMode?.let { builder.set(it, 3.toByte()) }
        keys.afRegionMode?.let { builder.set(it, 0.toByte()) }

        keys.awbColorCompensationAb?.let { builder.set(it, 0) }
        keys.awbColorCompensationGm?.let { builder.set(it, 0) }

        keys.stillSkinSmoothLevel?.let { builder.set(it, 0) }
        keys.stillHdrMode?.let { builder.set(it, 0.toByte()) }
        keys.powerSaveMode?.let { builder.set(it, 0.toByte()) }
        keys.exposureTimeLimit?.let { builder.set(it, 0L) }

        keys.fusionMode?.let { builder.set(it, 0.toByte()) }
        keys.vagueControlMode?.let { builder.set(it, 0.toByte()) }
        keys.videoStabilizationMode?.let { builder.set(it, 0.toByte()) }
        keys.wbMode?.let { builder.set(it, 0.toByte()) }

        keys.logicalMultiCameraMode?.let { builder.set(it, 0.toByte()) }

        keys.sceneDetectMode?.let { builder.set(it, 0.toByte()) }
        keys.conditionDetectMode?.let { builder.set(it, 0.toByte()) }
        keys.faceSmileScoresMode?.let { builder.set(it, 0.toByte()) }

        keys.faceSelectTrigger?.let { builder.set(it, 0.toByte()) }
        keys.objectSelectTrigger?.let { builder.set(it, 0.toByte()) }

        if (activeArrayWidth > 0 && activeArrayHeight > 0) {
            val area = intArrayOf(0, 0, activeArrayWidth, activeArrayHeight)

            keys.faceSelectTriggerArea?.let { builder.set(it, area) }
            keys.objectSelectTriggerArea?.let { builder.set(it, area) }
        }

        if (focalLengthMm > 0f) {
            keys.focalLength?.let { builder.set(it, focalLengthMm) }
        }
    }

    fun applyCinemaLook(builder: CaptureRequest.Builder, lookId: Int) {
        SonyVendorKeys.cinemaProfile?.let { builder.set(it, lookId.toByte()) }
    }

    fun applyManualExposureHints(builder: CaptureRequest.Builder, iso: Int, exposureTimeNs: Long) {
        SonyVendorKeys.aeMode?.let { builder.set(it, 0.toByte()) }

        SonyVendorKeys.exposureTimeHint?.let { builder.set(it, exposureTimeNs) }

        SonyVendorKeys.sensitivityHint?.let { builder.set(it, iso) }
    }
}
