package dev.shaaanuu.xcamera.camera.core

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler

class CameraController(context: Context) {

    private val cameraManager =
        context.applicationContext.getSystemService(CameraManager::class.java)
            ?: error("Camera2 service unavailable")

    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    @SuppressLint("MissingPermission")
    fun openCamera(cameraId: String, callback: CameraDevice.StateCallback, handler: Handler) {
        cameraManager.openCamera(cameraId, callback, handler)
    }
}
