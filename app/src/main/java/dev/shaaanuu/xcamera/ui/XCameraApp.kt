package dev.shaaanuu.xcamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.core.content.*
import dev.shaaanuu.xcamera.R
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraEngine
import dev.shaaanuu.xcamera.ui.cinema.*
import dev.shaaanuu.xcamera.ui.navigation.*
import dev.shaaanuu.xcamera.ui.photo.*
import dev.shaaanuu.xcamera.ui.video.*

val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Medium))

@Composable
fun XCameraApp() {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    val settingsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasCameraPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Camera permission required")

            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant permission")
            }

            Button(
                onClick = {
                    val intent =
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    settingsLauncher.launch(intent)
                }
            ) {
                Text("Open App Settings")
            }
        }
        return
    }

    var selectedMode by rememberSaveable { mutableStateOf(CameraMode.CINEMA) }

    val cinemaEngineState = remember { mutableStateOf<CinemaCameraEngine?>(null) }
    val isRecording = remember { mutableStateOf(false) }

    LaunchedEffect(selectedMode) {
        if (selectedMode != CameraMode.CINEMA) {
            cinemaEngineState.value?.stop()
            cinemaEngineState.value = null
            isRecording.value = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedMode) {
            CameraMode.PHOTO -> PhotoScreen()
            CameraMode.VIDEO -> VideoScreen()
            CameraMode.CINEMA ->
                CinemaScreen(engineState = cinemaEngineState, recordingState = isRecording)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ShutterButton(
                recording = isRecording.value,
                enabled = selectedMode == CameraMode.CINEMA && cinemaEngineState.value != null,
                onClick = {
                    val engine = cinemaEngineState.value ?: return@ShutterButton
                    if (isRecording.value) engine.stopRecording() else engine.startRecording()
                },
            )

            Spacer(modifier = Modifier.height(42.dp))

            CameraModeNavigation(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
            )
        }
    }
}

@Composable
fun ShutterButton(
    recording: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val outerSize = 100.dp
    val innerSize = 83.dp
    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier =
            modifier
                .size(outerSize)
                .clip(CircleShape)
                .background(Color(0xFF111318).copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f * alpha),
                    shape = CircleShape,
                )
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF2F2F2))
            )
        } else {
            Box(
                modifier =
                    Modifier.size(innerSize)
                        .clip(CircleShape)
                        .background(Color(0xFFD62735).copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun CameraModeNavigation(
    selectedMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = CameraMode.entries
    val selectedIndex = modes.indexOf(selectedMode)

    val selectorWidth = 338.dp
    val selectorHeight = 42.dp
    val pillWidth = 105.dp
    val pillHeight = 32.dp

    val segmentWidth = selectorWidth / modes.size
    val pillOffset by
        animateDpAsState(
            targetValue = (segmentWidth * selectedIndex) + ((segmentWidth - pillWidth) / 2f),
            label = "pill_offset",
        )

    Box(modifier = modifier.width(selectorWidth).height(selectorHeight)) {
        Box(
            modifier =
                Modifier.matchParentSize()
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF0B0D10).copy(alpha = 0.52f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50),
                    )
        )

        Box(
            modifier =
                Modifier.offset(x = pillOffset, y = 4.dp)
                    .width(pillWidth)
                    .height(pillHeight)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF111318).copy(alpha = 0.45f))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(50),
                    )
        )

        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            modes.forEach { mode ->
                Box(
                    modifier =
                        Modifier.weight(1f).fillMaxHeight().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onModeSelected(mode)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label.uppercase(),
                        color = Color(0xFFF2F2F2),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = JetBrainsMono,
                    )
                }
            }
        }
    }
}
