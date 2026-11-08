package dev.shaaanuu.xcamera.ui.cinema

import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.max

private const val LOGICAL_DISPLAY_ROTATION = Surface.ROTATION_90
private const val CONTROL_RESERVED_HEIGHT_DP = 112

@Composable
internal fun CinemaPreview(
    previewSize: android.util.Size?,
    onSurfaceReady: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSurfaceReady = rememberUpdatedState(onSurfaceReady)
    val currentOnSurfaceDestroyed = rememberUpdatedState(onSurfaceDestroyed)
    val currentPreviewSize = rememberUpdatedState(previewSize)

    AndroidView(
        factory = { context ->
            val rootLayout =
                FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    clipChildren = false
                    clipToPadding = false
                }

            val landscapeHost =
                FrameLayout(context).apply {
                    clipChildren = false
                    clipToPadding = false
                }

            val textureView = TextureView(context)

            landscapeHost.addView(textureView)
            rootLayout.addView(landscapeHost)

            fun updateViewfinder() {
                val rootW =
                    rootLayout.width.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.widthPixels

                val rootH =
                    rootLayout.height.takeIf { it > 0 }
                        ?: context.resources.displayMetrics.heightPixels

                val landscapeW = max(rootW, rootH)
                val landscapeH = minOf(rootW, rootH)

                val hostParams = landscapeHost.layoutParams as? FrameLayout.LayoutParams

                if (
                    hostParams == null ||
                        hostParams.width != landscapeW ||
                        hostParams.height != landscapeH
                ) {
                    landscapeHost.layoutParams =
                        FrameLayout.LayoutParams(landscapeW, landscapeH, Gravity.CENTER)
                }

                landscapeHost.rotation = 90f

                val size = currentPreviewSize.value ?: android.util.Size(2520, 1080)
                val ratio = size.width.toFloat() / size.height.toFloat()

                val availableW = landscapeW
                val reservedHeight =
                    (CONTROL_RESERVED_HEIGHT_DP * context.resources.displayMetrics.density).toInt()

                val availableH = max(1, landscapeH - reservedHeight)

                val viewW: Int
                val viewH: Int

                if (landscapeW / availableH.toFloat() > ratio) {
                    viewH = availableH
                    viewW = (availableH * ratio).toInt()
                } else {
                    viewW = availableW
                    viewH = (availableW / ratio).toInt()
                }

                val textureParams = textureView.layoutParams as? FrameLayout.LayoutParams

                if (
                    textureParams == null ||
                        textureParams.width != viewW ||
                        textureParams.height != viewH ||
                        textureParams.gravity != Gravity.CENTER
                ) {
                    textureView.layoutParams =
                        FrameLayout.LayoutParams(viewW, viewH, Gravity.CENTER)
                }

                textureView.pivotX = viewW / 2f
                textureView.pivotY = viewH / 2f

                val scale = max(landscapeW / viewW.toFloat(), landscapeH / viewH.toFloat())

                textureView.scaleX = scale
                textureView.scaleY = scale

                configureTransform(textureView, currentPreviewSize.value)
            }

            rootLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateViewfinder() }

            textureView.surfaceTextureListener =
                object : TextureView.SurfaceTextureListener {

                    override fun onSurfaceTextureAvailable(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        currentOnSurfaceReady.value(surface, width, height)
                        configureTransform(textureView, currentPreviewSize.value)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: SurfaceTexture,
                        width: Int,
                        height: Int,
                    ) {
                        configureTransform(textureView, currentPreviewSize.value)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        currentOnSurfaceDestroyed.value()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }

            updateViewfinder()
            rootLayout
        },
        update = { rootLayout ->
            val landscapeHost = rootLayout.getChildAt(0) as FrameLayout
            val textureView = landscapeHost.getChildAt(0) as TextureView

            val size = currentPreviewSize.value ?: android.util.Size(2520, 1080)
            val ratio = size.width.toFloat() / size.height.toFloat()

            val rootW = rootLayout.width
            val rootH = rootLayout.height

            if (rootW > 0 && rootH > 0) {
                val landscapeW = max(rootW, rootH)
                val landscapeH = minOf(rootW, rootH)

                val hostParams = landscapeHost.layoutParams as FrameLayout.LayoutParams
                if (hostParams.width != landscapeW || hostParams.height != landscapeH) {
                    landscapeHost.layoutParams =
                        FrameLayout.LayoutParams(landscapeW, landscapeH, Gravity.CENTER)
                }

                landscapeHost.rotation = 90f

                val reservedHeight =
                    (CONTROL_RESERVED_HEIGHT_DP * rootLayout.resources.displayMetrics.density)
                        .toInt()

                val availableH = max(1, landscapeH - reservedHeight)

                val viewW: Int
                val viewH: Int

                if (landscapeW / availableH.toFloat() > ratio) {
                    viewH = availableH
                    viewW = (availableH * ratio).toInt()
                } else {
                    viewW = landscapeW
                    viewH = (landscapeW / ratio).toInt()
                }

                textureView.layoutParams = FrameLayout.LayoutParams(viewW, viewH, Gravity.CENTER)

                textureView.pivotX = viewW / 2f
                textureView.pivotY = viewH / 2f

                val scale = max(landscapeW / viewW.toFloat(), landscapeH / viewH.toFloat())

                textureView.scaleX = scale
                textureView.scaleY = scale
            }

            configureTransform(textureView, currentPreviewSize.value)
        },
        modifier = modifier,
    )
}

private fun configureTransform(textureView: TextureView, previewSize: android.util.Size?) {
    if (previewSize == null) return

    val viewW = textureView.width
    val viewH = textureView.height

    if (viewW == 0 || viewH == 0) return

    val previewW = previewSize.width
    val previewH = previewSize.height

    val rotation = LOGICAL_DISPLAY_ROTATION

    val matrix = Matrix()

    val viewRect = RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())

    val bufferRect = RectF(0f, 0f, previewH.toFloat(), previewW.toFloat())

    val centerX = viewRect.centerX()
    val centerY = viewRect.centerY()

    if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())

        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)

        val scale = max(viewH.toFloat() / previewH, viewW.toFloat() / previewW)

        matrix.postScale(scale, scale, centerX, centerY)

        matrix.postRotate(90f * (rotation - 2), centerX, centerY)
    } else if (rotation == Surface.ROTATION_180) {
        matrix.postRotate(180f, centerX, centerY)
    } else {
        matrix.postRotate(-90f, centerX, centerY)
    }

    textureView.setTransform(matrix)
}
