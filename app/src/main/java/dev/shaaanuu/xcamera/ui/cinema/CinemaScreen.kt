package dev.shaaanuu.xcamera.ui.cinema

import android.util.Size
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraConfig
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraEngine
import dev.shaaanuu.xcamera.camera.cinema.CinemaState
import dev.shaaanuu.xcamera.ui.JetBrainsMono

private val ISO_VALUES = listOf(64, 80, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800)

private val SHUTTER_VALUES =
    listOf(
        2.8f,
        3.6f,
        4.5f,
        5.6f,
        7.2f,
        9f,
        11.2f,
        14.1f,
        18f,
        22.5f,
        28.1f,
        36f,
        45f,
        56.3f,
        72f,
        90f,
        112.5f,
        144f,
        150f,
        172.8f,
        180f,
        225f,
        300f,
        360f,
    )

@Composable
internal fun CinemaScreen(
    engineState: MutableState<CinemaCameraEngine?>,
    recordingState: MutableState<Boolean>,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var engine by remember { mutableStateOf<CinemaCameraEngine?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    var cinemaState by remember { mutableStateOf<CinemaState?>(null) }
    var lookMenuVisible by remember { mutableStateOf(false) }
    var foreground by remember { mutableStateOf(true) }

    val currentEngine = rememberUpdatedState(engine)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(engine, cinemaState?.sessionReady) {
        engineState.value =
            if (engine != null && cinemaState?.sessionReady == true) engine else null
    }

    LaunchedEffect(cinemaState?.recording) { recordingState.value = cinemaState?.recording == true }

    DisposableEffect(Unit) {
        onDispose {
            currentEngine.value?.stop()
            engineState.value = null
            recordingState.value = false
        }
    }

    val exposure = cinemaState?.exposure
    val isoSelected = exposure?.iso?.takeIf { it > 0 } ?: 400
    val shutterSelected = exposure?.shutterAngle?.takeIf { it > 0f } ?: 360f
    val shutterExposureLabel = formatExposureLabel(exposure?.shutterNanos ?: 0L)
    val lookId = cinemaState?.lookId ?: CinemaCameraConfig.DEFAULT_LOOK_ID
    val sessionReady = cinemaState?.sessionReady == true

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (foreground) {
            CinemaPreview(
                previewSize = previewSize,
                modifier = Modifier.fillMaxSize(),
                onSurfaceReady = { surfaceTexture, width, height ->
                    if (engine == null) {
                        val newEngine =
                            CinemaCameraEngine(
                                context,
                                object : CinemaCameraEngine.Listener {
                                    override fun onPreviewSizeSelected(size: Size) {
                                        previewSize = size
                                    }

                                    override fun onReady() = Unit

                                    override fun onError(message: String) {
                                        engine?.stop()
                                        engine = null
                                        cinemaState = null
                                        engineState.value = null
                                        recordingState.value = false
                                        lookMenuVisible = false
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }

                                    override fun onStateChanged(state: CinemaState) {
                                        cinemaState = state
                                    }

                                    override fun onAutoSampleFailed(reason: String) {
                                        Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )

                        engine = newEngine

                        try {
                            newEngine.start(
                                surfaceTexture = surfaceTexture,
                                viewWidth = width,
                                viewHeight = height,
                            )
                        } catch (error: Throwable) {
                            newEngine.stop()
                            engine = null
                        }
                    }
                },
                onSurfaceDestroyed = {
                    engine?.stop()
                    engine = null
                    previewSize = null
                    cinemaState = null
                    engineState.value = null
                    recordingState.value = false
                    lookMenuVisible = false
                },
            )
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CinemaLookButton(
                lookId = lookId,
                expanded = lookMenuVisible,
                enabled = sessionReady,
                onToggle = { lookMenuVisible = !lookMenuVisible },
            )
            Text(
                text = "4k · 120",
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
            )
        }

        AnimatedVisibility(
            visible = lookMenuVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 80.dp, start = 16.dp),
        ) {
            CinemaLookCard(
                lookId = lookId,
                enabled = sessionReady,
                onLookSelected = { id ->
                    engine?.setLook(id)
                    lookMenuVisible = false
                },
            )
        }

        CinemaExposureControls(
            isoValues = ISO_VALUES,
            isoSelected = isoSelected,
            isoPending = exposure?.isoPending == true,
            shutterValues = SHUTTER_VALUES,
            shutterSelected = shutterSelected,
            shutterExposureLabel = shutterExposureLabel,
            shutterPending = exposure?.shutterPending == true,
            enabled = sessionReady,
            onIsoSelected = { target -> engine?.setIso(target) },
            onAutoIso = { engine?.requestAutoIso() },
            onShutterSelected = { target -> engine?.setShutter(target) },
            onAutoShutter = { engine?.requestAutoShutter() },
            modifier =
                Modifier.align(Alignment.BottomCenter).padding(bottom = 260.dp).fillMaxWidth(),
        )
    }
}

private fun formatExposureLabel(ns: Long): String {
    if (ns <= 0L) return "auto"
    val denom = Math.rint(1.0e9 / ns).toLong()
    return "1/$denom SEC"
}
