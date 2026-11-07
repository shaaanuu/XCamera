package dev.shaaanuu.xcamera.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaaanuu.xcamera.ui.cinema.CinemaScreen
import dev.shaaanuu.xcamera.ui.navigation.CameraMode
import dev.shaaanuu.xcamera.ui.photo.PhotoScreen
import dev.shaaanuu.xcamera.ui.video.VideoScreen

@Composable
fun XCameraApp() {
    var selectedMode by rememberSaveable {
        mutableStateOf(CameraMode.CINEMA)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (selectedMode) {
            CameraMode.PHOTO -> PhotoScreen()
            CameraMode.VIDEO -> VideoScreen()
            CameraMode.CINEMA -> CinemaScreen()
        }

        CameraModeNavigation(
            selectedMode = selectedMode,
            onModeSelected = { selectedMode = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CameraModeNavigation(
    selectedMode: CameraMode,
    onModeSelected: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CameraMode.entries.forEach { mode ->
            val isSelected = mode == selectedMode

            Text(
                text = mode.label,
                fontSize = 16.sp,
                fontWeight = if (isSelected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}