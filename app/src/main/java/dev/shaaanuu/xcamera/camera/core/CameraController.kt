package dev.shaaanuu.xcamera.camera.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

class CameraController(context: Context) {

    private val cameraManager =
        context.applicationContext.getSystemService(CameraManager::class.java)
            ?: error("Camera2 service unavailable")

    fun getCameraCharacteristics(cameraId: String): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }
}
