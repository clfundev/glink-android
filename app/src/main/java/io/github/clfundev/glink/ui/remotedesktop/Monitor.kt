package io.github.clfundev.glink.ui.remotedesktop

import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

private const val TAG = "Monitor"

@Composable
fun Monitor(imageBitmap: ImageBitmap?, monitorMode: MonitorMode, onAction: (Action) -> Unit) {
    var originalSize by remember { mutableStateOf(IntSize.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Transform states
    var scale by remember { mutableFloatStateOf(1f) }
    var pinchOffsetX by remember { mutableFloatStateOf(0f) }
    var pinchOffsetY by remember { mutableFloatStateOf(0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Boundary limits
    var minScale by remember { mutableFloatStateOf(1f) }
    var maxScale by remember { mutableFloatStateOf(1f) }
    var maxLeft by remember { mutableFloatStateOf(0f) }
    var minRight by remember { mutableFloatStateOf(0f) }
    var maxTop by remember { mutableFloatStateOf(0f) }
    var minBottom by remember { mutableFloatStateOf(0f) }

    // Animation states
    var animationJob by remember { mutableStateOf<Job?>(null) }
    var isAnimating by remember { mutableStateOf(false) }
    var animationTargetScale by remember { mutableFloatStateOf(1f) }
    var animationScaleCenter by remember { mutableStateOf(Offset.Zero) }

    // Vertical drag frequency control
    var lastDragTime = 0L
    val cachedDragAmounts = mutableListOf<Float>()

    fun initTransform() {
        if (originalSize.width == 0 || originalSize.height == 0 ||
            viewportSize.width == 0 || viewportSize.height == 0
        ) return

        pinchOffsetX = 0f
        pinchOffsetY = 0f

        val widthRatio = viewportSize.width.toFloat() / originalSize.width
        val heightRatio = viewportSize.height.toFloat() / originalSize.height

        if (widthRatio > heightRatio) {
            minScale = heightRatio
            maxLeft = (viewportSize.width - originalSize.width * minScale) / 2f
            minRight = maxLeft + originalSize.width * minScale
            maxTop = 0f
            minBottom = viewportSize.height.toFloat()
        } else {
            minScale = widthRatio
            maxLeft = 0f
            minRight = viewportSize.width.toFloat()
            maxTop = (viewportSize.height - originalSize.height * minScale) / 2f
            minBottom = maxTop + originalSize.height * minScale
        }

        scale = minScale
        maxScale = minScale * 4
        panOffsetX = maxLeft
        panOffsetY = maxTop
    }

    fun handleZoom(targetScale: Float, scaleCenter: Offset) {
        if (monitorMode == MonitorMode.INPUT) return

        val prevLeft = pinchOffsetX + panOffsetX
        val prevTop = pinchOffsetY + panOffsetY
        val prevScale = scale

        val newScale = targetScale.coerceIn(minScale, maxScale)

        // X-axis transformation
        val dx = (scaleCenter.x - prevLeft) * (1 - newScale / prevScale)
        var newPinchOffsetX = pinchOffsetX + dx
        val newLeft = panOffsetX + newPinchOffsetX
        val newRight = newLeft + originalSize.width * newScale

        if (newLeft > maxLeft) {
            newPinchOffsetX -= newLeft - maxLeft
        } else if (newRight < minRight) {
            newPinchOffsetX += minRight - newRight
        }

        // Y-axis transformation
        val dy = (scaleCenter.y - prevTop) * (1 - newScale / prevScale)
        var newPinchOffsetY = pinchOffsetY + dy
        val newTop = panOffsetY + newPinchOffsetY
        val newBottom = newTop + originalSize.height * newScale

        if (newTop > maxTop) {
            newPinchOffsetY -= newTop - maxTop
        } else if (newBottom < minBottom) {
            newPinchOffsetY += minBottom - newBottom
        }

        scale = newScale
        pinchOffsetX = newPinchOffsetX
        pinchOffsetY = newPinchOffsetY
    }

    fun handlePan(offset: Offset) {
        if (monitorMode == MonitorMode.INPUT) return

        var newPanOffsetX = panOffsetX + offset.x
        val newLeft = pinchOffsetX + newPanOffsetX
        val newRight = newLeft + originalSize.width * scale

        if (newLeft > maxLeft) {
            newPanOffsetX -= newLeft - maxLeft
        } else if (newRight < minRight) {
            newPanOffsetX += minRight - newRight
        }

        var newPanOffsetY = panOffsetY + offset.y
        val newTop = pinchOffsetY + newPanOffsetY
        val newBottom = newTop + originalSize.height * scale

        if (newTop > maxTop) {
            newPanOffsetY -= newTop - maxTop
        } else if (newBottom < minBottom) {
            newPanOffsetY += minBottom - newBottom
        }

        panOffsetX = newPanOffsetX
        panOffsetY = newPanOffsetY
    }

    fun handleDoubleTap(offset: Offset) {
        if (monitorMode == MonitorMode.INPUT) {
            val transformX = (offset.x - pinchOffsetX - panOffsetX) / scale
            val transformY = (offset.y - pinchOffsetY - panOffsetY) / scale
            onAction(
                Action.DoubleLeftClick(
                    transformX / originalSize.width,
                    transformY / originalSize.height
                )
            )
        } else {
            val endScale: Float
            val scaleX: Float
            val scaleY: Float

            if (scale > minScale) {
                val left = pinchOffsetX + panOffsetX
                val right = left + originalSize.width * scale
                val top = pinchOffsetY + panOffsetY
                val bottom = top + originalSize.height * scale

                scaleX = (minRight * left - maxLeft * right) /
                        (left + minRight - right - maxLeft)
                scaleY = (minBottom * top - maxTop * bottom) /
                        (top + minBottom - bottom - maxTop)
                endScale = minScale
            } else {
                scaleX = offset.x
                scaleY = offset.y
                endScale = maxScale
            }

            // Start animation
            animationTargetScale = endScale
            animationScaleCenter = Offset(scaleX, scaleY)
            isAnimating = true
        }
    }

    fun handleLongPress(offset: Offset) {
        if (monitorMode == MonitorMode.DISPLAY) return
        val transformX = (offset.x - pinchOffsetX - panOffsetX) / scale
        val transformY = (offset.y - pinchOffsetY - panOffsetY) / scale
        onAction(
            Action.RightClick(
                transformX / originalSize.width,
                transformY / originalSize.height
            )
        )
    }

    fun handleTap(offset: Offset) {
        if (monitorMode == MonitorMode.DISPLAY) return
        val transformX = (offset.x - pinchOffsetX - panOffsetX) / scale
        val transformY = (offset.y - pinchOffsetY - panOffsetY) / scale
        onAction(
            Action.LeftClick(
                transformX / originalSize.width,
                transformY / originalSize.height
            )
        )
    }

    fun handleVerticalDrag(dragAmount: Float) {
        if (monitorMode == MonitorMode.DISPLAY) return

        val currentTime = SystemClock.uptimeMillis()
        val interval = currentTime - lastDragTime

        // remove obsolete data
        if (interval > 1000L) cachedDragAmounts.clear()

        cachedDragAmounts.add(dragAmount / scale)
        if (interval > 100L) {
            val amount = cachedDragAmounts.sum().roundToInt()
            onAction(Action.Scroll(amount))
            lastDragTime = currentTime
            cachedDragAmounts.clear()
        }
    }

    fun cancelAnimation() {
        if (isAnimating) {
            animationJob?.cancel()
            isAnimating = false
            Log.i(TAG, "Zoom animation cancelled")
        }
    }

    // Initial transform when sizes change
    LaunchedEffect(originalSize, viewportSize) {
        initTransform()
    }

    // Animation effect for double tap
    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            Log.i(TAG, "Zoom animation started")
            animationJob = coroutineContext[Job]
            animate(
                initialValue = scale,
                targetValue = animationTargetScale,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { value, _ ->
                handleZoom(value, animationScaleCenter)
            }
            isAnimating = false
            Log.i(TAG, "Zoom animation ended")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewportSize = it }
            .pointerInput(monitorMode) {
                if (monitorMode == MonitorMode.INPUT) return@pointerInput
                detectTransformGestures { centroid, pan, zoom, _ ->
                    cancelAnimation()
                    handleZoom(scale * zoom, centroid)
                    handlePan(pan)
                }
            }
            .pointerInput(monitorMode) {
                when (monitorMode) {
                    MonitorMode.DISPLAY -> {
                        detectTapGestures(
                            onDoubleTap = { handleDoubleTap(it) }
                        )
                    }

                    MonitorMode.INPUT -> {
                        detectTapGestures(
                            onLongPress = { handleLongPress(it) },
                            onDoubleTap = { handleDoubleTap(it) },
                            onTap = { handleTap(it) }
                        )
                    }
                }
            }
            .pointerInput(monitorMode) {
                if (monitorMode == MonitorMode.DISPLAY) return@pointerInput
                detectVerticalDragGestures { _, dragAmount ->
                    handleVerticalDrag(dragAmount)
                }
            }
    ) {
        val offsetX = pinchOffsetX + panOffsetX
        val offsetY = pinchOffsetY + panOffsetY
        Log.d(TAG, "offset = ($offsetX, $offsetY), scale = $scale")
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Monitor",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onSizeChanged { originalSize = it }
                    .graphicsLayer {
                        translationX = offsetX
                        translationY = offsetY
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            )
        }
    }
}

enum class MonitorMode {
    /**
     * 显示模式，手势仅用于移动、缩放画面
     */
    DISPLAY,

    /**
     * 输入模式，手势事件向外部传递，由外部负责向服务器发送指令
     */
    INPUT
}

@Serializable
sealed interface Action {
    @Serializable
    @SerialName("lclick")
    data class LeftClick(val xRatio: Float, val yRatio: Float) : Action

    @Serializable
    @SerialName("rclick")
    data class RightClick(val xRatio: Float, val yRatio: Float) : Action

    @Serializable
    @SerialName("dlclick")
    data class DoubleLeftClick(val xRatio: Float, val yRatio: Float) : Action

    @Serializable
    @SerialName("scroll")
    data class Scroll(val amount: Int) : Action

    @Serializable
    @SerialName("key")
    data class KeyboardInput(val keyName: String) : Action

    @Serializable
    @SerialName("text")
    data class TextInput(val text: String) : Action
}

private val customJson = Json { encodeDefaults = true }

fun Action.toJson() = customJson.encodeToString(this)