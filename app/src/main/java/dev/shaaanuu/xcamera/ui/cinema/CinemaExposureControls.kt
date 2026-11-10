package dev.shaaanuu.xcamera.ui.cinema

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shaaanuu.xcamera.ui.JetBrainsMono
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal val ControlSurface = Color(0xFF1C1D22)
private val AutoPillBackground = Color(0xFF292A30)
internal val PrimaryText = Color(0xFFFFFFFF)
private val SecondaryText = Color(0xFF92929B)
private val DialBaselineColor = Color(0xFF45464D)
internal val SelectionIndicator = Color(0xFFD62735)
private val MeteringIndicator = Color(0xFFFF9800)

private enum class ActiveControl {
    NONE,
    ISO,
    SHUTTER,
}

@Composable
internal fun CinemaExposureControls(
    isoValues: List<Int>,
    isoSelected: Int,
    isoPending: Boolean,
    shutterValues: List<Float>,
    shutterSelected: Float,
    shutterExposureLabel: String,
    shutterPending: Boolean,
    enabled: Boolean,
    onIsoSelected: (Int) -> Unit,
    onAutoIso: () -> Unit,
    onShutterSelected: (Float) -> Unit,
    onAutoShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeControl by remember { mutableStateOf(ActiveControl.NONE) }

    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = activeControl != ActiveControl.NONE,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.padding(bottom = 44.dp),
        ) {
            when (activeControl) {
                ActiveControl.ISO -> {
                    ExposureControlCard(
                        primaryValue = isoSelected.toString(),
                        secondaryValue = null,
                        pending = isoPending,
                        enabled = enabled,
                        onAuto = onAutoIso,
                    ) {
                        SteppedDial(
                            values = isoValues.map { it.toString() },
                            selectedIndex = isoValues.indexOf(isoSelected).coerceAtLeast(0),
                            onSelectedIndexChange = { idx -> onIsoSelected(isoValues[idx]) },
                            enabled = enabled,
                        )
                    }
                }
                ActiveControl.SHUTTER -> {
                    ExposureControlCard(
                        primaryValue = formatShutterAngle(shutterSelected),
                        secondaryValue = shutterExposureLabel,
                        pending = shutterPending,
                        enabled = enabled,
                        onAuto = onAutoShutter,
                    ) {
                        SteppedDial(
                            values = shutterValues.map { formatShutterDialLabel(it) },
                            selectedIndex =
                                shutterValues
                                    .indexOfFirst { it == shutterSelected }
                                    .coerceAtLeast(0),
                            onSelectedIndexChange = { idx ->
                                onShutterSelected(shutterValues[idx])
                            },
                            enabled = enabled,
                        )
                    }
                }
                else -> {}
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlTextButton("ISO", activeControl == ActiveControl.ISO) {
                activeControl =
                    if (activeControl == ActiveControl.ISO) ActiveControl.NONE
                    else ActiveControl.ISO
            }
            ControlTextButton("Shutter", activeControl == ActiveControl.SHUTTER) {
                activeControl =
                    if (activeControl == ActiveControl.SHUTTER) ActiveControl.NONE
                    else ActiveControl.SHUTTER
            }
            ControlTextButton("WB", false) {}
            ControlTextButton("Lens", false) {}
            ControlTextButton("Focus", false) {}
        }
    }
}

@Composable
private fun ControlTextButton(text: String, isActive: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (isActive) SelectionIndicator else PrimaryText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = JetBrainsMono,
        modifier =
            Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(8.dp),
    )
}

@Composable
internal fun ExposureControlCard(
    primaryValue: String,
    secondaryValue: String?,
    pending: Boolean = false,
    enabled: Boolean = true,
    onAuto: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    dial: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.45f

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ControlSurface.copy(alpha = alpha))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = primaryValue,
            color = PrimaryText.copy(alpha = alpha),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = JetBrainsMono,
            lineHeight = 26.sp,
        )

        if (secondaryValue != null) {
            Text(
                text = secondaryValue,
                color = SecondaryText.copy(alpha = alpha),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                fontFamily = JetBrainsMono,
            )
        }

        dial()

        if (onAuto != null) {
            Spacer(Modifier.height(6.dp))
            AutoPill(pending = pending, enabled = enabled, onClick = onAuto)
        }
    }
}

@Composable
private fun AutoPill(pending: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (pending) MeteringIndicator else AutoPillBackground
    val textColor = if (pending) Color(0xFF101114) else SecondaryText
    val alpha = if (enabled) 1f else 0.45f

    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(bg.copy(alpha = alpha))
                .clickable(enabled = enabled) { onClick() }
                .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AUTO",
            color = textColor.copy(alpha = alpha),
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            fontFamily = JetBrainsMono,
        )
    }
}

@Composable
internal fun SteppedDial(
    values: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    itemPitch: Dp = 50.dp,
    dialHeight: Dp = 48.dp,
) {
    if (values.isEmpty()) return

    val density = LocalDensity.current
    val pitchPx = with(density) { itemPitch.toPx() }
    val scope = rememberCoroutineScope()

    val animatedIndex = remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex) {
        animate(
            initialValue = animatedIndex.floatValue,
            targetValue = selectedIndex.toFloat(),
            animationSpec = tween(220, easing = FastOutSlowInEasing),
        ) { value, _ ->
            animatedIndex.floatValue = value
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .height(dialHeight)
                .pointerInput(values.size, enabled, selectedIndex) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            animatedIndex.floatValue =
                                (animatedIndex.floatValue - dragAmount / pitchPx).coerceIn(
                                    0f,
                                    values.lastIndex.toFloat(),
                                )
                        },
                        onDragEnd = {
                            val target =
                                animatedIndex.floatValue.roundToInt().coerceIn(values.indices)
                            if (target != selectedIndex) {
                                onSelectedIndexChange(target)
                            } else {
                                scope.launch {
                                    animate(
                                        initialValue = animatedIndex.floatValue,
                                        targetValue = target.toFloat(),
                                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    ) { v, _ ->
                                        animatedIndex.floatValue = v
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                animate(
                                    initialValue = animatedIndex.floatValue,
                                    targetValue = selectedIndex.toFloat(),
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                ) { v, _ ->
                                    animatedIndex.floatValue = v
                                }
                            }
                        },
                    )
                }
                .pointerInput(values.size, enabled, selectedIndex) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val centerX = size.width / 2f
                        val tapIndex =
                            ((offset.x - centerX) / pitchPx + animatedIndex.floatValue)
                                .roundToInt()
                                .coerceIn(values.indices)
                        if (tapIndex != selectedIndex) onSelectedIndexChange(tapIndex)
                    }
                }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val centerX = widthPx / 2f
        val current = animatedIndex.floatValue

        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .height(1.dp)
                    .background(DialBaselineColor)
        )

        Box(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(2.dp)
                    .height(11.dp)
                    .background(SelectionIndicator)
        )

        values.forEachIndexed { index, label ->
            val dxPx = centerX + (index - current) * pitchPx
            if (dxPx < -pitchPx * 1.2f || dxPx > widthPx + pitchPx * 1.2f) {
                return@forEachIndexed
            }

            val distance = abs(index - current)
            val clamped = distance.coerceIn(0f, 3f)
            val alpha = (1f - clamped / 3.4f).coerceIn(0.2f, 1f)
            val fontSize = (14f - clamped * 1.5f).sp

            Box(
                modifier =
                    Modifier.align(Alignment.TopStart)
                        .offset { IntOffset((dxPx - pitchPx / 2f).roundToInt(), 0) }
                        .width(itemPitch)
                        .height(dialHeight - 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = PrimaryText.copy(alpha = alpha),
                    fontSize = fontSize,
                    fontWeight = if (distance < 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                    fontFamily = JetBrainsMono,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatShutterAngle(angle: Float): String {
    val isInteger = angle == angle.toInt().toFloat()
    return if (isInteger) "${angle.toInt()}.0°" else "$angle°"
}

private fun formatShutterDialLabel(angle: Float): String {
    val isInteger = angle == angle.toInt().toFloat()
    return if (isInteger) angle.toInt().toString() else angle.toString()
}
