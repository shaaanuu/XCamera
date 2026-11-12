package dev.shaaanuu.xcamera.camera.cinema

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Handler
import dev.shaaanuu.xcamera.camera.core.exposure.ExposureSnapshot
import dev.shaaanuu.xcamera.camera.core.exposure.writeExposure

internal class CinemaExposure(
    private val frameDurationNs: Long,
    private val handler: Handler,
    private val onChanged: () -> Unit,
    private val onAutoSampleFailed: (String) -> Unit,
) {
    companion object {
        const val PENDING_NONE = 0
        const val PENDING_ISO = 1
        const val PENDING_SHUTTER = 2

        private const val SKIP_FRAMES = 3
        private const val MAX_WAIT_FRAMES = 120
        private const val SAMPLE_TIMEOUT_MS = 10_000L
        private const val EXPOSURE_TOLERANCE_NS = 1_000L

        private val ISO_SERIES =
            intArrayOf(
                0,
                25,
                32,
                40,
                50,
                64,
                80,
                100,
                125,
                160,
                200,
                250,
                320,
                400,
                500,
                640,
                800,
                1000,
                1250,
                1600,
                2000,
                2500,
                3200,
            )

        private val SHUTTER_ANGLES =
            floatArrayOf(
                0f,
                360f,
                300f,
                225f,
                180f,
                172.8f,
                150f,
                144f,
                112.5f,
                90f,
                72f,
                56.3f,
                45f,
                36f,
                28.1f,
                22.5f,
                18f,
                14.1f,
                11.2f,
                9f,
                7.2f,
                5.6f,
                4.5f,
                3.6f,
                2.8f,
                2.3f,
                1.8f,
                1.4f,
            )
    }

    private val availableIso = mutableListOf<Int>()
    private val availableShutterAngles = mutableListOf<Float>()

    private var currentIsoIndex = 0
    private var currentShutterIndex = 0

    @Volatile private var manualIso: Int? = null
    @Volatile private var manualExposureNs: Long? = null

    @Volatile private var sampledIso: Int = 0
    @Volatile private var sampledExposureNs: Long = 0L

    @Volatile private var pendingSample: Int = PENDING_NONE
    @Volatile private var sampleResults: Int = 0
    @Volatile private var lastResultIso: Int = 0
    @Volatile private var lastResultExposureNs: Long = 0L

    private var isoMinInUse: Int = 0
    private var isoMaxInUse: Int = 0

    private var sampleTimeout: Runnable? = null

    fun buildOptions(sensorIsoLower: Int, sensorIsoUpper: Int) {
        availableIso.clear()
        availableShutterAngles.clear()

        val isoUpper = minOf(sensorIsoUpper, CinemaCameraConfig.ISO_800_CAP)
        for (v in ISO_SERIES) {
            if (v == 0) continue
            if (v >= sensorIsoLower && v <= isoUpper) availableIso.add(v)
        }
        for (a in SHUTTER_ANGLES) {
            if (a == 0f) continue
            val ns = shutterNanos(a)
            if (ns >= CinemaCameraConfig.VIDEO_MIN_SHUTTER_NS && ns <= frameDurationNs) {
                availableShutterAngles.add(a)
            }
        }

        isoMinInUse = availableIso.firstOrNull() ?: 0
        isoMaxInUse = availableIso.lastOrNull() ?: 0

        val isoIdx = availableIso.indexOf(CinemaCameraConfig.DEFAULT_ISO)
        currentIsoIndex = if (isoIdx >= 0) isoIdx else 0
        manualIso = if (isoIdx >= 0) CinemaCameraConfig.DEFAULT_ISO else null

        val shIdx = availableShutterAngles.indexOf(CinemaCameraConfig.DEFAULT_SHUTTER_ANGLE)
        currentShutterIndex = if (shIdx >= 0) shIdx else 0
        manualExposureNs =
            if (shIdx >= 0) shutterNanos(CinemaCameraConfig.DEFAULT_SHUTTER_ANGLE) else null
    }

    fun isSampling(): Boolean = pendingSample != PENDING_NONE

    fun cycleIso() {
        if (availableIso.isEmpty()) return
        currentIsoIndex = (currentIsoIndex + 1) % availableIso.size
        manualIso = availableIso[currentIsoIndex]
        cancelPendingSample()
        onChanged()
    }

    fun cycleShutter() {
        if (availableShutterAngles.isEmpty()) return
        currentShutterIndex = (currentShutterIndex + 1) % availableShutterAngles.size
        manualExposureNs = shutterNanos(availableShutterAngles[currentShutterIndex])
        cancelPendingSample()
        onChanged()
    }

    fun setIso(value: Int) {
        val index = availableIso.indexOf(value)
        if (index < 0) return
        currentIsoIndex = index
        manualIso = value
        cancelPendingSample()
        onChanged()
    }

    fun setShutter(value: Float) {
        val index = availableShutterAngles.indexOf(value)
        if (index < 0) return
        currentShutterIndex = index
        manualExposureNs = shutterNanos(value)
        cancelPendingSample()
        onChanged()
    }

    fun requestAutoIso() = requestSample(PENDING_ISO)

    fun requestAutoShutter() = requestSample(PENDING_SHUTTER)

    private fun requestSample(what: Int) {
        if (availableIso.isEmpty() || availableShutterAngles.isEmpty()) {
            onAutoSampleFailed("Camera did not report available values.")
            return
        }
        pendingSample = what
        sampleResults = 0
        scheduleTimeout(what)
        onChanged()
    }

    private fun cancelPendingSample() {
        if (pendingSample == PENDING_NONE) return
        pendingSample = PENDING_NONE
        sampleResults = 0
        clearTimeout()
    }

    fun onCaptureResult(iso: Int, exposureNs: Long, aeState: Int) {
        if (iso > 0) lastResultIso = iso
        if (exposureNs > 0L) lastResultExposureNs = exposureNs

        val want = pendingSample
        if (want == PENDING_NONE) return
        if (++sampleResults <= SKIP_FRAMES) return

        when (aeState) {
            CaptureResult.CONTROL_AE_STATE_INACTIVE -> {
                if (sampleResults > MAX_WAIT_FRAMES) {
                    pendingSample = PENDING_NONE
                    clearTimeout()
                    onAutoSampleFailed("Auto-exposure did not engage. Set manually.")
                    onChanged()
                }
            }
            CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> {
                pendingSample = PENDING_NONE
                clearTimeout()
                onAutoSampleFailed("Too dark. Set ISO and shutter manually.")
                onChanged()
            }
            CaptureResult.CONTROL_AE_STATE_CONVERGED -> acceptSample(want)
            CaptureResult.CONTROL_AE_STATE_SEARCHING,
            CaptureResult.CONTROL_AE_STATE_LOCKED,
            CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> {
                if (sampleResults > MAX_WAIT_FRAMES) {
                    pendingSample = PENDING_NONE
                    clearTimeout()
                    onAutoSampleFailed("Auto-exposure did not converge.")
                    onChanged()
                }
            }
            else -> {
                if (sampleResults > MAX_WAIT_FRAMES) {
                    pendingSample = PENDING_NONE
                    clearTimeout()
                    onAutoSampleFailed("Auto-exposure failed.")
                    onChanged()
                }
            }
        }
    }

    private fun acceptSample(want: Int) {
        val tooDark =
            isoMaxInUse > 0 &&
                lastResultIso >= isoMaxInUse &&
                lastResultExposureNs >= frameDurationNs - EXPOSURE_TOLERANCE_NS
        val tooBright =
            isoMinInUse > 0 &&
                lastResultIso <= isoMinInUse &&
                lastResultExposureNs <=
                    CinemaCameraConfig.VIDEO_MIN_SHUTTER_NS + EXPOSURE_TOLERANCE_NS

        if (tooDark) {
            pendingSample = PENDING_NONE
            clearTimeout()
            onAutoSampleFailed("Too dark. Set ISO and shutter manually.")
            onChanged()
            return
        }
        if (tooBright) {
            pendingSample = PENDING_NONE
            clearTimeout()
            onAutoSampleFailed("Too bright. Set ISO and shutter manually.")
            onChanged()
            return
        }

        if (want == PENDING_ISO && lastResultIso > 0) {
            val snapped = nearestIso(lastResultIso)
            val previous = manualIso ?: sampledIso
            sampledIso = snapped
            manualIso = null
            currentIsoIndex = availableIso.indexOf(snapped).coerceAtLeast(0)
            pendingSample = PENDING_NONE
            clearTimeout()
            onAutoSampleFailed(
                if (snapped == previous) "ISO sample: $snapped (unchanged)"
                else "ISO sample: $snapped"
            )
            onChanged()
            return
        }
        if (want == PENDING_SHUTTER && lastResultExposureNs > 0L) {
            val snapped = nearestAngleNs(lastResultExposureNs)
            sampledExposureNs = snapped
            manualExposureNs = null
            currentShutterIndex =
                availableShutterAngles.indexOfFirst { shutterNanos(it) == snapped }.coerceAtLeast(0)
            pendingSample = PENDING_NONE
            clearTimeout()
            onAutoSampleFailed("Shutter sample: ${timeLabelOf(snapped)}")
            onChanged()
        }
    }

    fun applyTo(builder: CaptureRequest.Builder) {
        val iso = manualIso ?: sampledIso
        val expNs = manualExposureNs ?: sampledExposureNs
        val manual = pendingSample == PENDING_NONE && iso > 0 && expNs > 0L
        builder.writeExposure(iso = iso, exposureNs = expNs, manual = manual)
    }

    fun snapshot(): ExposureSnapshot {
        val iso = manualIso ?: sampledIso
        val ns = manualExposureNs ?: sampledExposureNs
        val angle = selectedShutterAngle()

        val isoLabel = if (iso <= 0) "ISOauto" else "ISO$iso"
        val shutterLabel =
            when {
                manualExposureNs != null -> shutterFullLabel(angle)
                ns <= 0L -> "SHauto"
                else -> timeLabelOf(ns)
            }

        return ExposureSnapshot(
            isoLabel = isoLabel,
            shutterLabel = shutterLabel,
            isoManual = iso > 0,
            shutterManual = ns > 0L,
            isoPending = pendingSample == PENDING_ISO,
            shutterPending = pendingSample == PENDING_SHUTTER,
            shutterAngle = angle,
            shutterNanos = ns,
            iso = iso,
        )
    }

    private fun selectedShutterAngle(): Float =
        availableShutterAngles.getOrNull(currentShutterIndex) ?: 0f

    private fun shutterNanos(angle: Float): Long {
        if (angle <= 0f) return 0L
        return ((angle / 360.0) * frameDurationNs).toLong()
    }

    private fun timeLabelOf(ns: Long): String {
        if (ns <= 0L) return "auto"
        val denom = Math.rint(1.0e9 / ns).toLong()
        return "1/$denom"
    }

    private fun shutterLabelOf(angle: Float): String =
        if (angle == Math.rint(angle.toDouble()).toFloat()) "${angle.toInt()}.0"
        else angle.toString()

    private fun shutterFullLabel(angle: Float): String =
        "${shutterLabelOf(angle)} (${timeLabelOf(shutterNanos(angle))})"

    private fun nearestIso(metered: Int): Int {
        if (availableIso.isEmpty()) return metered
        var best = availableIso[0]
        var bestDiff = Long.MAX_VALUE
        for (v in availableIso) {
            val d = kotlin.math.abs(v.toLong() - metered)
            if (d < bestDiff) {
                bestDiff = d
                best = v
            }
        }
        return best
    }

    private fun nearestAngleNs(meteredNs: Long): Long {
        var best = 0L
        var bestDiff = Long.MAX_VALUE
        for (a in availableShutterAngles) {
            val ns = shutterNanos(a)
            val d = kotlin.math.abs(ns - meteredNs)
            if (d < bestDiff) {
                bestDiff = d
                best = ns
            }
        }
        return best
    }

    private fun scheduleTimeout(what: Int) {
        clearTimeout()
        val runnable = Runnable {
            if (pendingSample == what) {
                pendingSample = PENDING_NONE
                onAutoSampleFailed("Auto timed out. Set manually.")
                onChanged()
            }
        }
        sampleTimeout = runnable
        handler.postDelayed(runnable, SAMPLE_TIMEOUT_MS)
    }

    private fun clearTimeout() {
        sampleTimeout?.let { handler.removeCallbacks(it) }
        sampleTimeout = null
    }
}
