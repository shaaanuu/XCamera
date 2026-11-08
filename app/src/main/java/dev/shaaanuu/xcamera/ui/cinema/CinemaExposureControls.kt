package dev.shaaanuu.xcamera.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaaanuu.xcamera.camera.core.exposure.ExposureSnapshot
import dev.shaaanuu.xcamera.ui.JetBrainsMono

@Composable
internal fun CinemaExposureControls(
    state: ExposureSnapshot?,
    enabled: Boolean,
    onCycleIso: () -> Unit,
    onAutoIso: () -> Unit,
    onCycleShutter: () -> Unit,
    onAutoShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposureButton(
                label = state?.isoLabel ?: "ISO---",
                active = state?.isoManual == true,
                pending = state?.isoPending == true,
                enabled = enabled,
                minWidth = 96.dp,
                onClick = onCycleIso,
            )
            ExposureButton(
                label = "A-ISO",
                active = false,
                pending = state?.isoPending == true,
                enabled = enabled,
                minWidth = 72.dp,
                onClick = onAutoIso,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposureButton(
                label = state?.shutterLabel ?: "SH---",
                active = state?.shutterManual == true,
                pending = state?.shutterPending == true,
                enabled = enabled,
                minWidth = 160.dp,
                onClick = onCycleShutter,
            )
            ExposureButton(
                label = "A-SH",
                active = false,
                pending = state?.shutterPending == true,
                enabled = enabled,
                minWidth = 72.dp,
                onClick = onAutoShutter,
            )
        }
    }
}

@Composable
private fun ExposureButton(
    label: String,
    active: Boolean,
    pending: Boolean,
    enabled: Boolean,
    minWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg =
        when {
            pending -> Color(0xFFFF9800).copy(alpha = 0.85f)
            active -> Color(0xFF1976D2).copy(alpha = 0.85f)
            else -> Color(0xFF111318).copy(alpha = 0.55f)
        }
    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier =
            modifier
                .widthIn(min = minWidth)
                .clip(RoundedCornerShape(50))
                .background(bg)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.22f * alpha),
                    shape = RoundedCornerShape(50),
                )
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color(0xFFF2F2F2).copy(alpha = alpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JetBrainsMono,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
