package dev.shaaanuu.xcamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.*
import androidx.activity.result.contract.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.core.content.*
import dev.shaaanuu.xcamera.ui.cinema.*
import dev.shaaanuu.xcamera.ui.navigation.*
import dev.shaaanuu.xcamera.ui.photo.*
import dev.shaaanuu.xcamera.ui.video.*

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

    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedMode) {
            CameraMode.PHOTO -> PhotoScreen()
            CameraMode.VIDEO -> VideoScreen()
            CameraMode.CINEMA -> CinemaScreen()
        }

        CameraModeNavigation(
            selectedMode = selectedMode,
            onModeSelected = { selectedMode = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CameraModeNavigation(
    selectedMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode

            Text(
                text = mode.label,
                fontSize = 16.sp,
                fontWeight =
                    if (isSelected) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier.clickable { onModeSelected(mode) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
