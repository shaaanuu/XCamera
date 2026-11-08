package dev.shaaanuu.xcamera.camera.core.exposure

import android.hardware.camera2.CaptureRequest
import dev.shaaanuu.xcamera.camera.sony.SonyVendorKeys

/**
 * Writes ISO/exposure into a request. Camera2 cannot mix manual ISO with auto shutter, so [manual]
 * must be false unless both values are known.
 */
internal fun CaptureRequest.Builder.writeExposure(iso: Int, exposureNs: Long, manual: Boolean) {
    if (!manual || iso <= 0 || exposureNs <= 0L) {
        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        return
    }

    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
    SonyVendorKeys.aeMode?.let { set(it, 0.toByte()) }

    set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNs)
    set(CaptureRequest.SENSOR_SENSITIVITY, iso)

    SonyVendorKeys.exposureTimeHint?.let { set(it, exposureNs) }
    SonyVendorKeys.sensitivityHint?.let { set(it, iso) }
}
