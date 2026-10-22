package io.github.clfundev.glink.ui.remotedesktop

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.clfundev.glink.R
import io.github.clfundev.glink.discovery.ServerInfo
import io.github.clfundev.glink.ui.theme.Green
import io.github.clfundev.glink.ui.theme.Red
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.math.roundToInt

private const val TAG = "RemoteDesktopScreen"

@Composable
fun RemoteDesktopScreen(serverInfo: ServerInfo?, onExit: () -> Unit) {
    var connected by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var monitorMode by remember { mutableStateOf(MonitorMode.DISPLAY) }
    var webSocket by remember { mutableStateOf<WebSocket?>(null) }

    val safePadding = WindowInsets.safeDrawing

    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    var controlSize by remember { mutableStateOf(IntSize.Zero) }

    var controlX by remember { mutableFloatStateOf(0f) }
    var controlY by remember { mutableFloatStateOf(0f) }

    var minControlX by remember { mutableFloatStateOf(0f) }
    var maxControlX by remember { mutableFloatStateOf(0f) }
    var minControlY by remember { mutableFloatStateOf(0f) }
    var maxControlY by remember { mutableFloatStateOf(0f) }

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val window = LocalActivity.current?.window

    fun connect() {
        Log.i(TAG, "Connecting to ${serverInfo?.address}")
        if (serverInfo?.address.isNullOrEmpty()) return
        val client = OkHttpClient()
        val request = Request.Builder().url(serverInfo.address).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connected = true
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val imageData = bytes.toByteArray()
                bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnect by user")
        webSocket = null
        connected = false
    }

    fun enableStickyImmersiveMode() {
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun disableStickyImmersiveMode() {
        window?.let {
            val controller = WindowCompat.getInsetsController(it, it.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        }
    }

    fun updateControlOffsetLimit() {
        val paddingLeft = safePadding.getLeft(density, LayoutDirection.Ltr).toFloat()
        val paddingRight = safePadding.getRight(density, LayoutDirection.Ltr).toFloat()
        val paddingTop = safePadding.getTop(density).toFloat()
        val paddingBottom = safePadding.getBottom(density).toFloat()
        minControlX = paddingLeft
        maxControlX = screenSize.width - paddingRight - controlSize.width
        minControlY = paddingTop
        maxControlY = screenSize.height - paddingBottom - controlSize.height
        // 控制栏初始位于顶部居中
        controlX = (minControlX + maxControlX) / 2
        controlY = minControlY
    }

    DisposableEffect(Unit) {
        connect()
        enableStickyImmersiveMode()

        onDispose {
            disconnect()
            disableStickyImmersiveMode()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged {
                screenSize = it
                updateControlOffsetLimit()
            }
    ) {
        Monitor(imageBitmap = bitmap?.asImageBitmap(), monitorMode = monitorMode) { action ->
            val command = action.toJson()
            webSocket?.send(command)
            Log.i(TAG, "Command sent: $command")
        }
        Card(
            modifier = Modifier
                .offset { IntOffset(controlX.roundToInt(), controlY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            controlX = (controlX + dragAmount.x).coerceIn(minControlX..maxControlX)
                            controlY = (controlY + dragAmount.y).coerceIn(minControlY..maxControlY)
                        }
                    )
                }
                .onSizeChanged {
                    controlSize = it
                    updateControlOffsetLimit()
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .width(IntrinsicSize.Min)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 服务器选择页返回按钮
                    IconButton(onClick = onExit) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Icon(
                                painter = painterResource(R.drawable.monitor_24px),
                                contentDescription = "Select server",
                                modifier = Modifier.size(24.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .offset(x = 2.dp)
                                    .background(if (connected) Green else Red, CircleShape)
                            )
                        }
                    }

                    // 输入和显示模式切换按钮
                    FilledIconToggleButton(
                        checked = monitorMode == MonitorMode.INPUT,
                        onCheckedChange = { checked ->
                            monitorMode = if (checked) MonitorMode.INPUT else MonitorMode.DISPLAY
                        },
                        colors = IconButtonDefaults.filledIconToggleButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = LocalContentColor.current,
                            checkedContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_touch_app_24),
                            contentDescription = "Mode",
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    // 播放、暂停按钮
                    IconButton(
                        onClick = {
                            if (connected) {
                                disconnect()
                            } else {
                                connect()
                            }
                        }
                    ) {
                        Icon(
                            painter = if (connected) {
                                painterResource(R.drawable.outline_pause_24)
                            } else {
                                painterResource(R.drawable.baseline_play_arrow_24)
                            },
                            contentDescription = if (connected) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                TextInputReceiver(
                    onTextInput = { text ->
                        webSocket?.send(Action.TextInput(text).toJson())
                    },
                    onBackspace = {
                        webSocket?.send(Action.KeyboardInput("backspace").toJson())
                    },
                    onEnter = {
                        webSocket?.send(Action.KeyboardInput("enter").toJson())
                    }
                )
            }
        }
    }

}