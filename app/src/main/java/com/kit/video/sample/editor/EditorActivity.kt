package com.kit.video.sample.editor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kit.video.sample.editor.ui.theme.VideoKitTheme
import kotlin.math.abs

class EditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VideoKitTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    VideoPickerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun VideoPickerScreen(modifier: Modifier = Modifier) {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // 创建视频选择器
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (selectedVideoUri == null) {
            // 显示选择视频按钮
            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("从相册选择视频")
            }

            Text(
                text = "提示：选择视频后可以调整选框查看局部",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            // 显示视频播放器和裁剪功能
            CropVideoPlayer(
                videoUri = selectedVideoUri!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { videoPickerLauncher.launch("video/*") }
                ) {
                    Text("选择其他视频")
                }
            }
        }
    }
}

@Composable
fun CropVideoPlayer(videoUri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // 视图尺寸
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 选框状态
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var isSelecting by remember { mutableStateOf(true) }

    // 应用裁剪后的缩放和偏移
    var appliedScale by remember { mutableFloatStateOf(1f) }
    var appliedOffsetX by remember { mutableFloatStateOf(0f) }
    var appliedOffsetY by remember { mutableFloatStateOf(0f) }

    // 创建和记住 ExoPlayer 实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // 初始化选框
    LaunchedEffect(viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0 && cropRect == Rect.Zero) {
            val width = viewSize.width * 0.6f
            val height = viewSize.height * 0.6f
            val left = (viewSize.width - width) / 2f
            val top = (viewSize.height - height) / 2f
            cropRect = Rect(left, top, left + width, top + height)
        }
    }

    // 当 videoUri 改变时更新播放内容
    LaunchedEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.play()
        isSelecting = true
        appliedScale = 1f
        appliedOffsetX = 0f
        appliedOffsetY = 0f
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewSize = coordinates.size
            }
    ) {
        // 视频播放器
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = !isSelecting
                }
            },
            update = { playerView ->
                playerView.useController = !isSelecting
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = appliedScale
                    scaleY = appliedScale
                    translationX = appliedOffsetX
                    translationY = appliedOffsetY
                }
        )

        // 选择模式的蒙层和选框
        if (isSelecting) {
            SelectionOverlay(
                viewSize = viewSize,
                cropRect = cropRect,
                onCropRectChange = { cropRect = it }
            )

            // 确认按钮
            FloatingActionButton(
                onClick = {
                    // 计算缩放和偏移
                    val scaleX = viewSize.width / cropRect.width
                    val scaleY = viewSize.height / cropRect.height
                    appliedScale = minOf(scaleX, scaleY)

                    val centerX = cropRect.center.x
                    val centerY = cropRect.center.y
                    val viewCenterX = viewSize.width / 2f
                    val viewCenterY = viewSize.height / 2f

                    appliedOffsetX = (viewCenterX - centerX) * appliedScale
                    appliedOffsetY = (viewCenterY - centerY) * appliedScale

                    isSelecting = false
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = "确认")
            }
        } else {
            // 重新选择按钮
            FloatingActionButton(
                onClick = {
                    isSelecting = true
                    appliedScale = 1f
                    appliedOffsetX = 0f
                    appliedOffsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "重新选择")
            }
        }
    }
}

@Composable
fun SelectionOverlay(
    viewSize: IntSize,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit
) {
    var activeHandle by remember { mutableStateOf<ResizeHandle?>(null) }
    var isDraggingRect by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 半透明蒙层
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(cropRect) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // 优先检测角点
                            val handle = getResizeHandle(offset, cropRect)
                            if (handle != null) {
                                activeHandle = handle
                                isDraggingRect = false
                            } else if (cropRect.contains(offset)) {
                                // 在选框内部才允许拖动
                                isDraggingRect = true
                                activeHandle = null
                            }
                            // 注意：这里不再处理外部点击，因为这会阻止PlayerView接收事件
                        },
                        onDrag = { change, dragAmount ->
                            // 消费事件防止下层视图处理
                            change.consume()
                            
                            when {
                                activeHandle != null -> {
                                    // 调整选框大小
                                    val newRect = resizeCropRect(cropRect, activeHandle!!, dragAmount, viewSize)
                                    onCropRectChange(newRect)
                                }
                                isDraggingRect -> {
                                    // 拖动整个选框
                                    val newLeft = (cropRect.left + dragAmount.x).coerceIn(0f, viewSize.width - cropRect.width)
                                    val newTop = (cropRect.top + dragAmount.y).coerceIn(0f, viewSize.height - cropRect.height)
                                    onCropRectChange(
                                        Rect(
                                            newLeft,
                                            newTop,
                                            newLeft + cropRect.width,
                                            newTop + cropRect.height
                                        )
                                    )
                                }
                            }
                        },
                        onDragEnd = {
                            isDraggingRect = false
                            activeHandle = null
                        },
                        onDragCancel = {
                            isDraggingRect = false
                            activeHandle = null
                        }
                    )
                }

        ) {
            // 绘制暗色蒙层（选框外的区域）
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset.Zero,
                size = Size(size.width, cropRect.top)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, cropRect.top),
                size = Size(cropRect.left, cropRect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(cropRect.right, cropRect.top),
                size = Size(size.width - cropRect.right, cropRect.height)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, cropRect.bottom),
                size = Size(size.width, size.height - cropRect.bottom)
            )

            // 绘制选框边框（更粗更明显）
            drawRect(
                color = Color.White,
                topLeft = cropRect.topLeft,
                size = Size(cropRect.width, cropRect.height),
                style = Stroke(width = 4f)
            )

            // 绘制四个角的控制点（更大更明显）
            listOf(
                cropRect.topLeft,
                Offset(cropRect.right, cropRect.top),
                Offset(cropRect.left, cropRect.bottom),
                cropRect.bottomRight
            ).forEach { corner ->
                // 外圈白色
                drawCircle(
                    color = Color.White,
                    radius = 16f,
                    center = corner
                )
                // 内圈蓝色
                drawCircle(
                    color = Color(0xFF2196F3),
                    radius = 13f,
                    center = corner
                )
            }

            // 绘制九宫格辅助线
            val lineColor = Color.White.copy(alpha = 0.6f)
            val lineWidth = 1.5f

            // 垂直线
            drawLine(
                color = lineColor,
                start = Offset(cropRect.left + cropRect.width / 3f, cropRect.top),
                end = Offset(cropRect.left + cropRect.width / 3f, cropRect.bottom),
                strokeWidth = lineWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(cropRect.left + cropRect.width * 2f / 3f, cropRect.top),
                end = Offset(cropRect.left + cropRect.width * 2f / 3f, cropRect.bottom),
                strokeWidth = lineWidth
            )

            // 水平线
            drawLine(
                color = lineColor,
                start = Offset(cropRect.left, cropRect.top + cropRect.height / 3f),
                end = Offset(cropRect.right, cropRect.top + cropRect.height / 3f),
                strokeWidth = lineWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(cropRect.left, cropRect.top + cropRect.height * 2f / 3f),
                end = Offset(cropRect.right, cropRect.top + cropRect.height * 2f / 3f),
                strokeWidth = lineWidth
            )
        }

        // 提示文字
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "拖动移动 • 拖动角点调整大小",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

enum class ResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

// 增加防抖动处理
fun getResizeHandle(offset: Offset, rect: Rect): ResizeHandle? {
    val touchRadius = 50f

    return when {
        (offset - rect.topLeft).getDistance() < touchRadius -> ResizeHandle.TOP_LEFT
        (offset - Offset(rect.right, rect.top)).getDistance() < touchRadius -> ResizeHandle.TOP_RIGHT
        (offset - Offset(rect.left, rect.bottom)).getDistance() < touchRadius -> ResizeHandle.BOTTOM_LEFT
        (offset - rect.bottomRight).getDistance() < touchRadius -> ResizeHandle.BOTTOM_RIGHT
        else -> null
    }
}

// 修改resizeCropRect函数，增加边界检查
fun resizeCropRect(rect: Rect, handle: ResizeHandle, dragAmount: Offset, viewSize: IntSize): Rect {
    val minSize = 100f

    return when (handle) {
        ResizeHandle.TOP_LEFT -> {
            val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
            val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
            Rect(newLeft, newTop, rect.right, rect.bottom)
        }
        ResizeHandle.TOP_RIGHT -> {
            val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, viewSize.width.toFloat())
            val newTop = (rect.top + dragAmount.y).coerceIn(0f, rect.bottom - minSize)
            Rect(rect.left, newTop, newRight, rect.bottom)
        }
        ResizeHandle.BOTTOM_LEFT -> {
            val newLeft = (rect.left + dragAmount.x).coerceIn(0f, rect.right - minSize)
            val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, viewSize.height.toFloat())
            Rect(newLeft, rect.top, rect.right, newBottom)
        }
        ResizeHandle.BOTTOM_RIGHT -> {
            val newRight = (rect.right + dragAmount.x).coerceIn(rect.left + minSize, viewSize.width.toFloat())
            val newBottom = (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, viewSize.height.toFloat())
            Rect(rect.left, rect.top, newRight, newBottom)
        }
    }
}
