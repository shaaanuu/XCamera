package dev.shaaanuu.xcamera.camera.core

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.view.Surface
import java.util.concurrent.Executor

internal class CameraSession(
    private val cameraDevice: CameraDevice,
    private val callbackHandler: Handler,
) {

    @Volatile private var activeSession: CameraCaptureSession? = null

    @Volatile private var closed = false

    private var configurationStarted = false

    @Throws(CameraAccessException::class)
    @Synchronized
    fun configure(
        outputSurfaces: List<Surface>,
        sessionParameters: CaptureRequest? = null,
        onConfigured: (CameraCaptureSession) -> Unit,
        onConfigureFailed: (CameraCaptureSession) -> Unit,
    ) {
        check(!closed) { "CameraSession is closed" }

        check(!configurationStarted) { "CameraSession configuration already started" }

        require(outputSurfaces.isNotEmpty()) { "At least one output surface is required" }

        require(outputSurfaces.all { it.isValid }) { "One or more output surfaces are invalid" }

        configurationStarted = true

        val executor = Executor { command -> callbackHandler.post(command) }

        val configuration =
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputSurfaces.map { OutputConfiguration(it) },
                executor,
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(session: CameraCaptureSession) {
                        if (closed) {
                            session.close()
                            return
                        }

                        activeSession = session
                        onConfigured(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        configurationStarted = false
                        onConfigureFailed(session)
                    }

                    override fun onClosed(session: CameraCaptureSession) {
                        if (activeSession === session) {
                            activeSession = null
                        }
                    }
                },
            )

        sessionParameters?.let { configuration.setSessionParameters(it) }

        cameraDevice.createCaptureSession(configuration)
    }

    @Throws(CameraAccessException::class)
    fun setRepeatingRequest(
        request: CaptureRequest,
        callback: CameraCaptureSession.CaptureCallback? = null,
    ) {
        val session = checkNotNull(activeSession) { "Camera capture session is not configured" }

        session.setRepeatingRequest(request, callback, callbackHandler)
    }

    @Throws(CameraAccessException::class)
    fun capture(request: CaptureRequest, callback: CameraCaptureSession.CaptureCallback? = null) {
        val session = checkNotNull(activeSession) { "Camera capture session is not configured" }

        session.capture(request, callback, callbackHandler)
    }

    fun stopRepeating() {
        activeSession?.let { runCatching { it.stopRepeating() } }
    }

    fun abortCaptures() {
        activeSession?.let { runCatching { it.abortCaptures() } }
    }

    @Synchronized
    fun close() {
        if (closed) return

        closed = true
        configurationStarted = false

        activeSession?.let {
            runCatching { it.stopRepeating() }
            runCatching { it.abortCaptures() }
            it.close()
        }

        activeSession = null
    }
}
