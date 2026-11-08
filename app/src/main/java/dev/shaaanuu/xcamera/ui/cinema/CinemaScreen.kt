package dev.shaaanuu.xcamera.ui.cinema

import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraEngine
import dev.shaaanuu.xcamera.camera.cinema.CinemaState

@Composable
internal fun CinemaScreen(
    engineState: MutableState<CinemaCameraEngine?>,
    recordingState: MutableState<Boolean>,
) {
    val context = LocalContext.current

    var engine by remember { mutableStateOf<CinemaCameraEngine?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }
    var cinemaState by remember { mutableStateOf<CinemaState?>(null) }

    val currentEngine = rememberUpdatedState(engine)

    LaunchedEffect(engine) { engineState.value = engine }

    LaunchedEffect(cinemaState?.recording) { recordingState.value = cinemaState?.recording == true }

    DisposableEffect(Unit) {
        onDispose {
            currentEngine.value?.stop()
            engineState.value = null
            recordingState.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
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

                                override fun onError(message: String) = Unit

                                override fun onStateChanged(state: CinemaState) {
                                    cinemaState = state
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
            },
        )

        CinemaExposureControls(
            state = cinemaState?.exposure,
            enabled = engine != null,
            onCycleIso = { engine?.cycleIso() },
            onAutoIso = { engine?.requestAutoIso() },
            onCycleShutter = { engine?.cycleShutter() },
            onAutoShutter = { engine?.requestAutoShutter() },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 272.dp),
        )
    }
}
