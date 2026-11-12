package dev.shaaanuu.xcamera.camera.cinema

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import dev.shaaanuu.xcamera.camera.cinema.recording.HlgEncoder
import dev.shaaanuu.xcamera.camera.core.CameraController
import dev.shaaanuu.xcamera.camera.core.CameraSession
import dev.shaaanuu.xcamera.camera.core.exposure.ExposureSnapshot
import dev.shaaanuu.xcamera.camera.sony.SonyVendorKeys
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal data class CinemaState(
    val exposure: ExposureSnapshot,
    val recording: Boolean,
    val lookId: Int,
    val sessionReady: Boolean,
)

internal class CinemaCameraEngine(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onPreviewSizeSelected(size: Size)

        fun onReady()

        fun onError(message: String)

        fun onStateChanged(state: CinemaState)

        fun onAutoSampleFailed(reason: String)
    }

    companion object {
        private const val TAG = "XCamera-CinemaEngine"
        private val RECORDING_DIRECTORY = "${Environment.DIRECTORY_DCIM}/XCamera/Cinema"
    }

    private val controller = CameraController(context)

    private val cameraThread = HandlerThread("XCamera-Cinema").apply { start() }
    private val handler = Handler(cameraThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val encoder = HlgEncoder()

    private val exposure =
        CinemaExposure(
            frameDurationNs = CinemaCameraConfig.FRAME_DURATION_NS,
            handler = handler,
            onChanged = {
                handler.post {
                    if (!closed) {
                        rebuildRepeating()
                        postState()
                    }
                }
            },
            onAutoSampleFailed = { reason ->
                mainHandler.post { if (!closed) listener.onAutoSampleFailed(reason) }
            },
        )

    @Volatile private var closed = false
    private var started = false
    @Volatile private var recording = false
    @Volatile private var sessionReady = false
    @Volatile private var currentLookId: Int = CinemaCameraConfig.DEFAULT_LOOK_ID

    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraSession? = null

    private var previewSurface: Surface? = null
    private var encoderSurface: Surface? = null

    private var sessionConfigured = false

    private var cachedCharacteristics: CameraCharacteristics? = null
    private var activeArrayWidth = 0
    private var activeArrayHeight = 0
    private var focalLengthMm = 0f

    private var recordingUri: Uri? = null
    private var recordingPfd: ParcelFileDescriptor? = null

    private val captureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
                val expNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                val aeState =
                    result.get(CaptureResult.CONTROL_AE_STATE)
                        ?: CaptureResult.CONTROL_AE_STATE_INACTIVE
                exposure.onCaptureResult(iso, expNs, aeState)
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
                if (closed) return@post
                try {
                    val characteristics =
                        controller.getCameraCharacteristics(CinemaCameraConfig.CAMERA_ID)

                    val previewSize = choosePreviewSize(characteristics)

                    surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
                    previewSurface = Surface(surfaceTexture)
                    encoderSurface = encoder.start()

                    if (closed) {
                        cleanup()
                        return@post
                    }

                    mainHandler.post { if (!closed) listener.onPreviewSizeSelected(previewSize) }
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

    fun setIso(value: Int) = handler.post { if (!closed) exposure.setIso(value) }

    fun setShutter(value: Float) = handler.post { if (!closed) exposure.setShutter(value) }

    fun requestAutoIso() = handler.post { if (!closed) exposure.requestAutoIso() }

    fun requestAutoShutter() = handler.post { if (!closed) exposure.requestAutoShutter() }

    fun setLook(lookId: Int) = handler.post {
        if (closed) return@post
        if (lookId == currentLookId) return@post
        if (!CinemaCameraConfig.LOOK_IDS.contains(lookId)) return@post
        currentLookId = lookId
        rebuildRepeating()
        postState()
    }

    fun startRecording() = handler.post {
        if (closed || recording) return@post
        if (!sessionConfigured) return@post

        try {
            val (uri, pfd) = createRecordingEntry()
            recordingUri = uri
            recordingPfd = pfd

            encoder.beginRecording(pfd.fileDescriptor)

            recording = true
            rebuildRepeating()
            postState()
        } catch (error: Throwable) {
            Log.e(TAG, "startRecording failed", error)
            runCatching { recordingPfd?.close() }
            recordingPfd = null
            recordingUri?.let { deleteRecordingEntry(it) }
            recordingUri = null
            recording = false
        }
    }

    fun stopRecording() = handler.post {
        if (!recording) return@post

        recording = false
        encoder.stopRecording()
        rebuildRepeating()

        val uri = recordingUri
        val pfd = recordingPfd
        val samples = encoder.samplesWritten

        recordingUri = null
        recordingPfd = null

        runCatching { pfd?.close() }

        if (samples == 0) {
            Log.e(TAG, "Recording produced no samples, deleting")
            uri?.let { deleteRecordingEntry(it) }
        } else {
            uri?.let { finalizeRecordingEntry(it) }
        }

        postState()
    }

    @Synchronized
    fun stop() {
        if (closed) return
        closed = true
        cleanup()
        cameraThread.quitSafely()
    }

    private fun createRecordingEntry(): Pair<Uri, ParcelFileDescriptor> {
        val resolver = context.contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val name = "XCamera_Cinema_$timestamp"

        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$name.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, RECORDING_DIRECTORY)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

        val uri =
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")

        val pfd =
            resolver.openFileDescriptor(uri, "w")
                ?: error("MediaStore openFileDescriptor returned null")

        return uri to pfd
    }

    private fun finalizeRecordingEntry(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    private fun deleteRecordingEntry(uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
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

        val sessionParameters = buildRequest(camera, characteristics, recording = false).build()

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
                            buildRequest(camera, characteristics, recording = false)
                                .apply { addTarget(preview) }
                                .build()

                        session.setRepeatingRequest(request, captureCallback)
                        sessionConfigured = true
                        sessionReady = true

                        mainHandler.post {
                            if (!closed) {
                                listener.onReady()
                                listener.onStateChanged(
                                    CinemaState(
                                        exposure.snapshot(),
                                        recording = recording,
                                        lookId = currentLookId,
                                        sessionReady = true,
                                    )
                                )
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

        val rec = recording
        try {
            val builder =
                buildRequest(camera, characteristics, recording = rec).apply {
                    addTarget(preview)
                    if (rec) {
                        encoderSurface?.takeIf { it.isValid }?.let { addTarget(it) }
                    }
                }
            session.setRepeatingRequest(builder.build(), captureCallback)
        } catch (error: Throwable) {
            fail("Rebuild request failed: ${error.message}")
        }
    }

    private fun postState() {
        val state =
            CinemaState(
                exposure.snapshot(),
                recording = recording,
                lookId = currentLookId,
                sessionReady = sessionReady,
            )
        mainHandler.post { if (!closed) listener.onStateChanged(state) }
    }

    private fun buildRequest(
        camera: CameraDevice,
        characteristics: CameraCharacteristics,
        recording: Boolean,
    ): CaptureRequest.Builder {
        val sampling = exposure.isSampling()
        return camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            set(
                CaptureRequest.CONTROL_SCENE_MODE,
                if (sampling) CaptureRequest.CONTROL_SCENE_MODE_DISABLED
                else CaptureRequest.CONTROL_SCENE_MODE_PORTRAIT,
            )
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            exposure.applyTo(this)

            set(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                CinemaCameraConfig.pickFpsRange(characteristics, recording),
            )
            set(
                CaptureRequest.SENSOR_FRAME_DURATION,
                CinemaCameraConfig.frameDurationFor(recording),
            )

            val oisModes =
                characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            if (oisModes?.contains(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON,
                )
            }

            if (sampling) {
                // Sony's aeMode = 0 (set for manual) blocks standard AE.
                // Override to 1 to re-enable the HAL's AE path during the
                // sample. All other Sony pipeline keys are skipped here
                // because exposureTimeLimit / aeRegionMode also interfere.
                SonyVendorKeys.aeMode?.let { set(it, 1.toByte()) }
            } else {
                SonyCinemaSettings.applyCinemaPipeline(
                    builder = this,
                    activeArrayWidth = activeArrayWidth,
                    activeArrayHeight = activeArrayHeight,
                    focalLengthMm = focalLengthMm,
                )
            }
            SonyCinemaSettings.applyCinemaLook(builder = this, lookId = currentLookId)
        }
    }

    @Synchronized
    private fun fail(message: String) {
        if (closed) return
        closed = true
        cleanup()
        cameraThread.quitSafely()
        mainHandler.post { listener.onError(message) }
    }

    @Synchronized
    private fun cleanup() {
        if (recording) {
            recording = false
            encoder.stopRecording()
            val samples = encoder.samplesWritten
            val uri = recordingUri
            val pfd = recordingPfd
            recordingUri = null
            recordingPfd = null
            runCatching { pfd?.close() }
            if (samples == 0) {
                Log.e(TAG, "Recording produced no samples, deleting")
                uri?.let { deleteRecordingEntry(it) }
            } else {
                uri?.let { finalizeRecordingEntry(it) }
            }
        }

        sessionConfigured = false
        sessionReady = false

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
