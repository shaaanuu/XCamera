package dev.shaaanuu.xcamera.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaaanuu.xcamera.camera.cinema.CinemaCameraConfig
import dev.shaaanuu.xcamera.ui.JetBrainsMono

@Composable
internal fun CinemaLookButton(
    lookId: Int,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.45f
    val label = CinemaCameraConfig.lookShortName(lookId)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = if (expanded) SelectionIndicator else Color.White.copy(alpha = alpha),
            fontSize = 14.sp,
            fontWeight = if (expanded) FontWeight.Bold else FontWeight.Medium,
            fontFamily = JetBrainsMono,
        )
    }
}

@Composable
internal fun CinemaLookCard(
    lookId: Int,
    enabled: Boolean,
    onLookSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.45f

    Column(
        modifier =
            modifier
                .width(IntrinsicSize.Max)
                .clip(RoundedCornerShape(16.dp))
                .background(ControlSurface.copy(alpha = alpha))
                .padding(vertical = 6.dp)
    ) {
        CinemaCameraConfig.LOOK_IDS.forEachIndexed { index, id ->
            val selected = id == lookId
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onLookSelected(id)
                        }
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = CinemaCameraConfig.LOOK_FULL[index],
                    color = if (selected) SelectionIndicator else PrimaryText.copy(alpha = alpha),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontFamily = JetBrainsMono,
                )
            }
        }
    }
}
