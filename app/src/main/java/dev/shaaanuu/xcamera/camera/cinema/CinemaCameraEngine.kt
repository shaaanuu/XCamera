package dev.shaaanuu.xcamera.camera.cinema

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import dev.shaaanuu.xcamera.camera.cinema.recording.HlgEncoder
import dev.shaaanuu.xcamera.camera.core.CameraController
import dev.shaaanuu.xcamera.camera.core.CameraSession
import dev.shaaanuu.xcamera.camera.core.exposure.ExposureSnapshot
import kotlin.math.abs

internal class CinemaCameraEngine(
    context: android.content.Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPreviewSizeSelected(size: Size)

        fun onReady()

        fun onError(message: String)

        fun onExposureChanged(state: ExposureSnapshot)
    }

    private val controller = CameraController(context)

    private val cameraThread = HandlerThread("XCamera-Cinema").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val encoder = HlgEncoder()

    private val exposure =
        CinemaExposure(frameDurationNs = CinemaCameraConfig.FRAME_DURATION_NS, handler = handler) {
            rebuildRepeating()
            postExposureState()
        }

    @Volatile private var closed = false
    private var started = false

    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraSession? = null

    private var previewSurface: Surface? = null
    private var encoderSurface: Surface? = null

    private var sessionConfigured = false

    private var cachedCharacteristics: CameraCharacteristics? = null
    private var activeArrayWidth = 0
    private var activeArrayHeight = 0
    private var focalLengthMm = 0f

    private val captureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                exposure.onCaptureResult(iso, expNs)
            }
        }

    @Synchronized
    fun start(surfaceTexture: SurfaceTexture, viewWidth: Int, viewHeight: Int) {
        check(!started) { "Cinema camera already started" }
        check(!closed) { "Cinema camera engine is closed" }
        require(viewWidth > 0 && viewHeight > 0) { "Preview view has invalid dimensions" }

        started = true

        check(
            handler.post {
                try {
                    val characteristics =
                        controller.getCameraCharacteristics(CinemaCameraConfig.CAMERA_ID)

                    val previewSize = choosePreviewSize(characteristics)

                    surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                    previewSurface = Surface(surfaceTexture)

                    mainHandler.post { if (!closed) listener.onPreviewSizeSelected(previewSize) }

                    encoderSurface = encoder.start()
                    openCamera(characteristics)
                } catch (error: Throwable) {
                    fail("Cinema initialization failed: ${error.message}")
                }
            }
        ) {
            "Cinema camera thread is not accepting work"
        }
    }

    fun cycleIso() = handler.post { if (!closed) exposure.cycleIso() }

    fun cycleShutter() = handler.post { if (!closed) exposure.cycleShutter() }

    fun requestAutoIso() = handler.post { if (!closed) exposure.requestAutoIso() }

    fun requestAutoShutter() = handler.post { if (!closed) exposure.requestAutoShutter() }

    @Synchronized
    fun stop() {
        if (closed) return
        closed = true
        cleanup()
        cameraThread.quitSafely()
    }

    private fun choosePreviewSize(characteristics: CameraCharacteristics): Size {
        val streamMap =
            checkNotNull(
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ) {
                "Camera has no stream configuration map"
            }

        val sizes =
            streamMap.getOutputSizes(SurfaceTexture::class.java)
                ?: error("Camera exposes no TextureView preview sizes")
        require(sizes.isNotEmpty()) { "Camera exposes no TextureView preview sizes" }

        val targetAspect =
            CinemaCameraConfig.CINEMA_ASPECT_WIDTH.toFloat() /
                CinemaCameraConfig.CINEMA_ASPECT_HEIGHT
        val recordingArea =
            CinemaCameraConfig.VIDEO_WIDTH.toLong() * CinemaCameraConfig.VIDEO_HEIGHT

        return sizes
            .filter { size ->
                val aspect = size.width.toFloat() / size.height
                val area = size.width.toLong() * size.height
                abs(aspect - targetAspect) <= CinemaCameraConfig.ASPECT_TOLERANCE &&
                    area < recordingArea
            }
            .maxByOrNull { it.width.toLong() * it.height }
            ?: error("No preview size matches the Cinema recording aspect ratio")
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(characteristics: CameraCharacteristics) {
        if (closed) return

        controller.openCamera(
            CinemaCameraConfig.CAMERA_ID,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (closed) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    configureCinemaSession(camera, characteristics)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    fail("Cinema camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    fail("Camera2 error: $error")
                }
            },
            handler,
        )
    }

    private fun configureCinemaSession(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
    ) {
        val preview = checkNotNull(previewSurface) { "Preview surface unavailable" }
        val encoderOutput = checkNotNull(encoderSurface) { "HLG encoder surface unavailable" }
        check(preview.isValid) { "Preview surface is invalid" }
        check(encoderOutput.isValid) { "HLG encoder surface is invalid" }

        cachedCharacteristics = characteristics
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        activeArrayWidth = activeArray?.width() ?: 0
        activeArrayHeight = activeArray?.height() ?: 0
        focalLengthMm =
            characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: 0f

        val sensRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val isoLow = sensRange?.lower ?: 0
        val isoHigh = sensRange?.upper ?: Int.MAX_VALUE
        exposure.buildOptions(isoLow, isoHigh)

        val sessionParameters = buildRequest(camera, characteristics).build()

        val session = CameraSession(cameraDevice = camera, callbackHandler = handler)
        cameraSession = session

        try {
            session.configure(
                outputSurfaces = listOf(preview, encoderOutput),
                sessionParameters = sessionParameters,
                onConfigured = { configuredSession ->
                    try {
                        if (closed) {
                            configuredSession.close()
                            return@configure
                        }

                        val request =
                            buildRequest(camera, characteristics)
                                .apply { addTarget(preview) }
                                .build()

                        session.setRepeatingRequest(request, captureCallback)
                        sessionConfigured = true

                        mainHandler.post {
                            if (!closed) {
                                listener.onReady()
                                listener.onExposureChanged(exposure.snapshot())
                            }
                        }
                    } catch (error: Throwable) {
                        fail("Preview request failed: ${error.message}")
                    }
                },
                onConfigureFailed = { fail("Cinema capture session configuration failed") },
            )
        } catch (error: Throwable) {
            fail("Cinema session configuration failed: ${error.message}")
        }
    }

    private fun rebuildRepeating() {
        if (closed || !sessionConfigured) return
        val camera = cameraDevice ?: return
        val session = cameraSession ?: return
        val characteristics = cachedCharacteristics ?: return
        val preview = previewSurface ?: return

        try {
            val request = buildRequest(camera, characteristics).apply { addTarget(preview) }.build()
            session.setRepeatingRequest(request, captureCallback)
        } catch (error: Throwable) {
            fail("Rebuild request failed: ${error.message}")
        }
    }

    private fun postExposureState() {
        val state = exposure.snapshot()
        mainHandler.post { if (!closed) listener.onExposureChanged(state) }
    }

    private fun buildRequest(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
    ): CaptureRequest.Builder {
        return camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            exposure.applyTo(this)

            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, choosePreviewFpsRange(characteristics))
            set(CaptureRequest.SENSOR_FRAME_DURATION, CinemaCameraConfig.PREVIEW_FRAME_DURATION_NS)

            val oisModes =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (oisModes?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
                )
            }

            SonyCinemaSettings.applyCinemaPipeline(
                builder = this,
                activeArrayWidth = activeArrayWidth,
                activeArrayHeight = activeArrayHeight,
                focalLengthMm = focalLengthMm,
            )
            SonyCinemaSettings.applyCinemaLook(
                builder = this,
                lookId = CinemaCameraConfig.DEFAULT_LOOK_ID,
            )
        }
    }

    private fun choosePreviewFpsRange(characteristics: CameraCharacteristics): Range<Int> {
        val ranges =
            characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?: emptyArray()

        return ranges.firstOrNull {
            it.lower == CinemaCameraConfig.PREVIEW_MIN_FPS &&
                it.upper == CinemaCameraConfig.NOMINAL_FPS
        }
            ?: ranges.firstOrNull {
                it.lower == CinemaCameraConfig.NOMINAL_FPS &&
                    it.upper == CinemaCameraConfig.NOMINAL_FPS
            }
            ?: error("Camera does not expose the required 30-60 or fixed-60 FPS range")
    }

    private fun fail(message: String) {
        if (closed) return
        closed = true
        cleanup()
        cameraThread.quitSafely()
        mainHandler.post { listener.onError(message) }
    }

    private fun cleanup() {
        sessionConfigured = false

        cameraSession?.close()
        cameraSession = null
        cameraDevice?.close()
        cameraDevice = null

        previewSurface?.release()
        previewSurface = null

        encoder.stop()
        encoderSurface = null
    }
}
