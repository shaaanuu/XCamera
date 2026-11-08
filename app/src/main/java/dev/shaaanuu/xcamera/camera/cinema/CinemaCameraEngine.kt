package dev.shaaanuu.xcamera.camera.cinema

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Range
import android.util.Size
import android.view.Surface
import dev.shaaanuu.xcamera.camera.cinema.recording.HlgEncoder
import dev.shaaanuu.xcamera.camera.core.CameraController
import dev.shaaanuu.xcamera.camera.core.CameraSession
import kotlin.math.abs

internal class CinemaCameraEngine(
    context: android.content.Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onPreviewSizeSelected(size: Size)

        fun onReady()

        fun onError(message: String)
    }

    private val controller = CameraController(context)

    private val cameraThread = HandlerThread("XCamera-Cinema").apply { start() }

    private val handler = Handler(cameraThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val encoder = HlgEncoder()

    @Volatile private var closed = false

    private var started = false

    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraSession? = null

    private var previewSurface: Surface? = null
    private var encoderSurface: Surface? = null

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

                    mainHandler.post {
                        if (!closed) {
                            listener.onPreviewSizeSelected(previewSize)
                        }
                    }

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

        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

        val focalLength =
            characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: 0f

        val sessionParameters =
            buildRequest(
                    camera = camera,
                    characteristics = characteristics,
                    activeArrayWidth = activeArray?.width() ?: 0,
                    activeArrayHeight = activeArray?.height() ?: 0,
                    focalLengthMm = focalLength,
                )
                .build()

        val outputs = listOf(preview, encoderOutput)

        configureSession(
            camera = camera,
            outputs = outputs,
            sessionParameters = sessionParameters,
            characteristics = characteristics,
            activeArrayWidth = activeArray?.width() ?: 0,
            activeArrayHeight = activeArray?.height() ?: 0,
            focalLengthMm = focalLength,
        )
    }

    private fun configureSession(
        camera: CameraDevice,
        outputs: List<Surface>,
        sessionParameters: CaptureRequest?,
        characteristics: CameraCharacteristics,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        focalLengthMm: Float,
    ) {
        val session = CameraSession(cameraDevice = camera, callbackHandler = handler)

        cameraSession = session

        try {
            session.configure(
                outputSurfaces = outputs,
                sessionParameters = sessionParameters,
                onConfigured = { configuredSession ->
                    try {
                        if (closed) {
                            configuredSession.close()
                            return@configure
                        }

                        val request =
                            buildRequest(
                                    camera = camera,
                                    characteristics = characteristics,
                                    activeArrayWidth = activeArrayWidth,
                                    activeArrayHeight = activeArrayHeight,
                                    focalLengthMm = focalLengthMm,
                                )
                                .apply { addTarget(checkNotNull(previewSurface)) }
                                .build()

                        session.setRepeatingRequest(request)

                        mainHandler.post {
                            if (!closed) {
                                listener.onReady()
                            }
                        }
                    } catch (error: Throwable) {
                        fail("Preview request failed: ${error.message}")
                    }
                },
                onConfigureFailed = { fail("Cinema capture session configuration failed") },
            )
        } catch (error: Throwable) {
            if (sessionParameters == null) {
                fail("Cinema session configuration failed: ${error.message}")
                return
            }

            cameraSession = null
            session.close()

            val fallbackSession = CameraSession(cameraDevice = camera, callbackHandler = handler)

            cameraSession = fallbackSession

            try {
                fallbackSession.configure(
                    outputSurfaces = outputs,
                    sessionParameters = null,
                    onConfigured = { configuredSession ->
                        try {
                            if (closed) {
                                configuredSession.close()
                                return@configure
                            }

                            val request =
                                buildRequest(
                                        camera = camera,
                                        characteristics = characteristics,
                                        activeArrayWidth = activeArrayWidth,
                                        activeArrayHeight = activeArrayHeight,
                                        focalLengthMm = focalLengthMm,
                                    )
                                    .apply { addTarget(checkNotNull(previewSurface)) }
                                    .build()

                            fallbackSession.setRepeatingRequest(request)

                            mainHandler.post {
                                if (!closed) {
                                    listener.onReady()
                                }
                            }
                        } catch (requestError: Throwable) {
                            fail("Preview request failed: ${requestError.message}")
                        }
                    },
                    onConfigureFailed = { fail("Cinema capture session configuration failed") },
                )
            } catch (fallbackError: Throwable) {
                fail("Cinema session fallback failed: ${fallbackError.message}")
            }
        }
    }

    private fun buildRequest(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        activeArrayWidth: Int,
        activeArrayHeight: Int,
        focalLengthMm: Float,
    ): CaptureRequest.Builder {
        return camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT)

            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)

            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)

            set(CaptureRequest.SENSOR_SENSITIVITY, CinemaCameraConfig.DEFAULT_ISO)

            set(CaptureRequest.SENSOR_EXPOSURE_TIME, CinemaCameraConfig.DEFAULT_EXPOSURE_NS)

            SonyCinemaSettings.applyManualExposureHints(
                builder = this,
                iso = CinemaCameraConfig.DEFAULT_ISO,
                exposureTimeNs = CinemaCameraConfig.DEFAULT_EXPOSURE_NS,
            )

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
        cameraSession?.close()
        cameraSession = null

        cameraDevice?.close()
        cameraDevice = null

        previewSurface?.release()
        previewSurface = null

        // Stop encoder only after camera session has been closed.
        encoder.stop()
        encoderSurface = null
    }

    @Synchronized
    fun stop() {
        if (closed) return

        closed = true
        cleanup()
        cameraThread.quitSafely()
    }
}
