package dev.shaaanuu.xcamera.ui.cinema

import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraEngine

@Composable
fun CinemaScreen() {
    val context = LocalContext.current

    var engine by remember { mutableStateOf<CinemaCameraEngine?>(null) }
    var previewSize by remember { mutableStateOf<Size?>(null) }

    val currentEngine = rememberUpdatedState(engine)

    DisposableEffect(Unit) { onDispose { currentEngine.value?.stop() } }

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
            },
        )
    }
}
