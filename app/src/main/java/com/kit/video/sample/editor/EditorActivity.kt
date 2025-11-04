package com.kit.video.sample.editor

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kit.video.sample.editor.ui.theme.VideoKitTheme
import kotlin.div

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

// 裁剪信息数据类
data class CropInfo(
    val previewRect: Rect,        // 用户选择的显示区域（相对于视频原始尺寸的归一化坐标）
    val videoSize: IntSize        // 视频原始尺寸（像素）
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerScreen(modifier: Modifier = Modifier) {

    var isCrop by remember { mutableStateOf(false) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

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

            // 裁剪比例模式 - 用户选择的比例模式（自由、16:9等）
            var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FREE) }

            // 用于存储裁剪信息的变量
            var cropInfo by remember { mutableStateOf<CropInfo?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {

                CropVideoPlayer(
                    videoUri = selectedVideoUri!!,
                    aspectRatioMode = aspectRatioMode,
                    onCropInfoChange = { cropInfo = it } // 接收完整的裁剪信息
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 提示文字
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (aspectRatioMode == AspectRatioMode.FREE)
                                "拖动移动 • 拖动角点调整大小"
                            else
                                "拖动移动 • 拖动角点调整大小 (保持${aspectRatioMode.displayName}比例)",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 比例模式选择器
                    Surface(
                        modifier = Modifier.padding(top = 3.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AspectRatioMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = aspectRatioMode == mode,
                                    onClick = {
                                        aspectRatioMode = mode
                                    },
                                    label = { Text(mode.displayName) }
                                )
                            }
                        }
                    }
                }


                FloatingActionButton(
                    onClick = {
                        isCrop = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "确认")
                }

                if (isCrop) {
                    // 显示半屏弹窗展示裁剪后的视频预览
                    ModalBottomSheet(
                        onDismissRequest = { isCrop = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
                        containerColor = Color.Black
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(600.dp)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "裁剪预览",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // 显示裁剪后的视频预览
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Red)
                                    .weight(1f)
                            ) {
                                selectedVideoUri?.let { uri ->
                                    cropInfo?.let { info ->
                                        CropVideoPreview(uri, info)
                                    }
                                }
                            }

                            cropInfo?.let { info ->
                                Column(
                                    modifier = Modifier.padding(top = 16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "视频原始尺寸: ${info.videoSize.width} x ${info.videoSize.height}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CropVideoPreview(videoUri: Uri, cropInfo: CropInfo) {
    val context = LocalContext.current

    // 创建并记住ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // 当视频URI改变时重新加载视频并重置状态
    LaunchedEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // 组件销毁时释放ExoPlayer资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // 使用裁剪区域的比例，而不是视频显示区域的比例
    val viewMaxSize = 300f
    val videoRatio = cropInfo.videoSize.width.toFloat() / cropInfo.videoSize.height.toFloat()
    Log.d("CropVideoPreview", "videoRatio: $videoRatio")

    val viewSize = if (videoRatio > 1f) {
        // 横向视频
        IntSize(viewMaxSize.toInt(), (viewMaxSize / videoRatio).toInt())
    } else {
        // 纵向视频
        IntSize((viewMaxSize / videoRatio).toInt(), viewMaxSize.toInt())
    }

    // 计算裁剪参数
    val cropParams = remember(cropInfo) {
        calculateCropParameters(viewSize, cropInfo)
    }

    Log.d("CropVideoPreview", "viewSize: $viewSize, cropInfo: $cropInfo, cropParams: $cropParams")
    Box(
        modifier = Modifier
            .size(viewSize.width.dp, viewSize.height.dp)
            .background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            update = { playerView ->
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = cropParams.scaleX
                    scaleY = cropParams.scaleY
                    translationX = cropParams.translationX
                    translationY = cropParams.translationY
                    clip = true

                }
        )
    }
}

// 裁剪参数数据类
data class CropParameters(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float
)

/**
 * 计算裁剪参数
 * 将用户选择的裁剪区域转换为视频播放器的缩放和平移参数
 */
fun calculateCropParameters(viewSize: IntSize, cropInfo: CropInfo): CropParameters {
    val normalizedLeft = cropInfo.previewRect.left
    val normalizedTop = cropInfo.previewRect.top
    val normalizedWidth = cropInfo.previewRect.width
    val normalizedHeight = cropInfo.previewRect.height

    // 缩放比例 = 放大以填充选框区域
    val scaleX = 1f / normalizedWidth
    val scaleY = 1f / normalizedHeight

    // 裁剪区域中心点（归一化）
    val centerX = normalizedLeft + normalizedWidth / 2f
    val centerY = normalizedTop + normalizedHeight / 2f

    // 偏移计算：将裁剪区域中心点对齐到视图中心
    val translationX = (0.5f - centerX) * viewSize.width * scaleX
    val translationY = (0.5f - centerY) * viewSize.height * scaleY

    return CropParameters(scaleX, scaleY, translationX, translationY)
}



// 裁剪比例模式枚举
enum class AspectRatioMode(val displayName: String, val ratio: Float?) {
    FREE("自由", null),
    RATIO_3_4("3:4", 3f / 4f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_16_9("16:9", 16f / 9f)
}

/**
 * 可裁剪的视频播放器组件
 *
 * @param videoUri 视频文件的URI
 * @param aspectRatioMode 裁剪比例模式
 * @param onCropInfoChange 回调函数，当裁剪信息发生变化时调用
 */
@Composable
fun CropVideoPlayer(
    videoUri: Uri,
    aspectRatioMode: AspectRatioMode,
    onCropInfoChange: (CropInfo) -> Unit = {},
) {
    val context = LocalContext.current

    // 视图尺寸状态 - 用于跟踪容器的大小
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    // 视频尺寸状态 - 从ExoPlayer获取的实际视频分辨率
    var videoSize by remember { mutableStateOf(IntSize.Zero) }

    // 创建并记住ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE

            // 监听视频尺寸变化
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                    videoSize = IntSize(size.width, size.height)
                }
            })
        }
    }

    // 当视频URI改变时重新加载视频并重置状态
    LaunchedEffect(videoUri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        exoPlayer.prepare()
        exoPlayer.play()
        videoSize = IntSize.Zero // 重置视频尺寸，等待新视频加载
    }

    // 组件销毁时释放ExoPlayer资源
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                viewSize = coordinates.size
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            update = { playerView ->
                playerView.useController = false
            },
            modifier = Modifier
                .fillMaxSize()
        )

        SelectionOverlay(
            viewSize = viewSize,
            videoSize = videoSize,
            aspectRatioMode = aspectRatioMode,
            onCropInfoChange = onCropInfoChange
        )
    }
}

// 创建初始裁剪框 - 根据视频实际尺寸和显示区域计算
fun createInitialCropRect(viewSize: IntSize, videoSize: IntSize, mode: AspectRatioMode): Rect {
    // 如果视频尺寸还未获取，使用视图尺寸
    if (videoSize.width == 0 || videoSize.height == 0) {
        val width = viewSize.width * 0.6f
        val height = viewSize.height * 0.6f
        val left = (viewSize.width - width) / 2f
        val top = (viewSize.height - height) / 2f
        return Rect(left, top, left + width, top + height)
    }

    // 计算视频在视图中的实际显示区域
    val videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
    val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()

    val actualVideoWidth: Float
    val actualVideoHeight: Float
    val videoOffsetX: Float
    val videoOffsetY: Float

    if (videoAspectRatio > viewAspectRatio) {
        // 视频更宽，上下留黑边
        actualVideoWidth = viewSize.width.toFloat()
        actualVideoHeight = viewSize.width.toFloat() / videoAspectRatio
        videoOffsetX = 0f
        videoOffsetY = (viewSize.height - actualVideoHeight) / 2f
    } else {
        // 视频更高，左右留黑边
        actualVideoHeight = viewSize.height.toFloat()
        actualVideoWidth = viewSize.height.toFloat() * videoAspectRatio
        videoOffsetX = (viewSize.width - actualVideoWidth) / 2f
        videoOffsetY = 0f
    }

    val ratio = mode.ratio

    return if (ratio != null) {
        // 固定比例模式 - 在视频实际显示区域内创建选框
        val maxWidth = actualVideoWidth * 0.8f
        val maxHeight = actualVideoHeight * 0.8f

        val width: Float
        val height: Float

        if (maxWidth / maxHeight < ratio) {
            // 宽度受限
            width = maxWidth
            height = width / ratio
        } else {
            // 高度受限
            height = maxHeight
            width = height * ratio
        }

        val left = videoOffsetX + (actualVideoWidth - width) / 2f
        val top = videoOffsetY + (actualVideoHeight - height) / 2f

        Rect(left, top, left + width, top + height)
    } else {
        // 自由模式 - 在视频实际显示区域内创建选框
        val width = actualVideoWidth * 0.7f
        val height = actualVideoHeight * 0.7f
        val left = videoOffsetX + (actualVideoWidth - width) / 2f
        val top = videoOffsetY + (actualVideoHeight - height) / 2f

        Rect(left, top, left + width, top + height)
    }
}

private fun calculateCropPreviewRect(
    videoSize: IntSize,
    cropRect: Rect,
    videoBounds: Rect
): CropInfo {
    val normalizedLeft = (cropRect.left - videoBounds.left) / videoBounds.width
    val normalizedTop = (cropRect.top - videoBounds.top) / videoBounds.height
    val normalizedWidth = cropRect.width / videoBounds.width
    val normalizedHeight = cropRect.height / videoBounds.height
    val previewRect = Rect(
        normalizedLeft,
        normalizedTop,
        normalizedLeft + normalizedWidth,
        normalizedTop + normalizedHeight
    )
    return CropInfo(previewRect, videoSize)
}

@Composable
fun SelectionOverlay(
    viewSize: IntSize,
    videoSize: IntSize,
    aspectRatioMode: AspectRatioMode,
    onCropInfoChange: (CropInfo) -> Unit
) {
    var activeHandle by remember { mutableStateOf<ResizeHandle?>(null) }
    var isDraggingRect by remember { mutableStateOf(false) }

    // 裁剪矩形状态 - 用户选择的裁剪区域
    var cropRect by remember { mutableStateOf(Rect.Zero) }

    // 计算视频在视图中的实际显示区域
    val videoBounds = remember(viewSize, videoSize) {
        if (videoSize.width == 0 || videoSize.height == 0) {
            Rect(0f, 0f, viewSize.width.toFloat(), viewSize.height.toFloat())
        } else {
            val videoAspectRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
            val viewAspectRatio = viewSize.width.toFloat() / viewSize.height.toFloat()

            if (videoAspectRatio > viewAspectRatio) {
                val actualVideoHeight = viewSize.width.toFloat() / videoAspectRatio
                val videoOffsetY = (viewSize.height - actualVideoHeight) / 2f
                Rect(0f, videoOffsetY, viewSize.width.toFloat(), videoOffsetY + actualVideoHeight)
            } else {
                val actualVideoWidth = viewSize.height.toFloat() * videoAspectRatio
                val videoOffsetX = (viewSize.width - actualVideoWidth) / 2f
                Rect(videoOffsetX, 0f, videoOffsetX + actualVideoWidth, viewSize.height.toFloat())
            }
        }
    }

    // 当裁剪信息变化时，通知外部
    LaunchedEffect(cropRect, videoBounds, videoSize) {
        if (cropRect != Rect.Zero && videoSize.width > 0 && videoSize.height > 0) {
            val previewRect = calculateCropPreviewRect(
                videoSize,
                cropRect,
                videoBounds
            )
            onCropInfoChange(previewRect)
        }
    }

    // 初始化选框 - 考虑视频和视图的尺寸
    LaunchedEffect(videoSize, aspectRatioMode) {
        if (viewSize.width > 0 && viewSize.height > 0 && videoSize.width > 0 && videoSize.height > 0) {
            cropRect = createInitialCropRect(viewSize, videoSize, aspectRatioMode)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(videoSize) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val handle = getResizeHandle(offset, cropRect)
                            if (handle != null) {
                                activeHandle = handle
                                isDraggingRect = false
                            } else if (cropRect.contains(offset)) {
                                isDraggingRect = true
                                activeHandle = null
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()

                            when {
                                activeHandle != null -> {
                                    val newRect = resizeCropRect(
                                        cropRect,
                                        activeHandle!!,
                                        dragAmount,
                                        aspectRatioMode,
                                        videoBounds
                                    )
                                    cropRect = newRect
                                }

                                isDraggingRect -> {
                                    val newLeft = (cropRect.left + dragAmount.x)
                                        .coerceIn(
                                            videoBounds.left,
                                            videoBounds.right - cropRect.width
                                        )
                                    val newTop = (cropRect.top + dragAmount.y)
                                        .coerceIn(
                                            videoBounds.top,
                                            videoBounds.bottom - cropRect.height
                                        )
                                    val newRect = Rect(
                                        newLeft, newTop,
                                        newLeft + cropRect.width,
                                        newTop + cropRect.height
                                    )

                                    cropRect = newRect
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
            // 绘制暗色蒙层
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

            // 绘制选框边框
            drawRect(
                color = Color.White,
                topLeft = cropRect.topLeft,
                size = Size(cropRect.width, cropRect.height),
                style = Stroke(width = 4f)
            )

            // 绘制四个角的控制点
            listOf(
                cropRect.topLeft,
                Offset(cropRect.right, cropRect.top),
                Offset(cropRect.left, cropRect.bottom),
                cropRect.bottomRight
            ).forEach { corner ->
                drawCircle(
                    color = Color.White,
                    radius = 16f,
                    center = corner
                )
                drawCircle(
                    color = Color(0xFF2196F3),
                    radius = 13f,
                    center = corner
                )
            }

            // 绘制九宫格辅助线
            val lineColor = Color.White.copy(alpha = 0.6f)
            val lineWidth = 1.5f

            drawLine(
                color = lineColor,
                start = Offset(
                    cropRect.left + cropRect.width / 3f,
                    cropRect.top
                ),
                end = Offset(
                    cropRect.left + cropRect.width / 3f,
                    cropRect.bottom
                ),
                strokeWidth = lineWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(
                    cropRect.left + cropRect.width * 2f / 3f,
                    cropRect.top
                ),
                end = Offset(
                    cropRect.left + cropRect.width * 2f / 3f,
                    cropRect.bottom
                ),
                strokeWidth = lineWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(
                    cropRect.left,
                    cropRect.top + cropRect.height / 3f
                ),
                end = Offset(
                    cropRect.right,
                    cropRect.top + cropRect.height / 3f
                ),
                strokeWidth = lineWidth
            )
            drawLine(
                color = lineColor,
                start = Offset(
                    cropRect.left,
                    cropRect.top + cropRect.height * 2f / 3f
                ),
                end = Offset(
                    cropRect.right,
                    cropRect.top + cropRect.height * 2f / 3f
                ),
                strokeWidth = lineWidth
            )
        }

    }
}

enum class ResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

fun getResizeHandle(offset: Offset, rect: Rect): ResizeHandle? {
    val touchRadius = 50f

    return when {
        (offset - rect.topLeft).getDistance() < touchRadius -> ResizeHandle.TOP_LEFT
        (offset - Offset(
            rect.right,
            rect.top
        )).getDistance() < touchRadius -> ResizeHandle.TOP_RIGHT

        (offset - Offset(
            rect.left,
            rect.bottom
        )).getDistance() < touchRadius -> ResizeHandle.BOTTOM_LEFT

        (offset - rect.bottomRight).getDistance() < touchRadius -> ResizeHandle.BOTTOM_RIGHT
        else -> null
    }
}

fun resizeCropRect(
    rect: Rect,
    handle: ResizeHandle,
    dragAmount: Offset,
    aspectRatioMode: AspectRatioMode,
    videoBounds: Rect
): Rect {
    // 最小尺寸限制（像素）
    val minSize = 150f
    val ratio = aspectRatioMode.ratio

    return if (ratio != null) {
        // 固定比例模式
        when (handle) {
            ResizeHandle.TOP_LEFT -> {
                val newLeft =
                    (rect.left + dragAmount.x).coerceIn(videoBounds.left, rect.right - minSize)
                val newTop =
                    (rect.top + dragAmount.y).coerceIn(videoBounds.top, rect.bottom - minSize)

                val widthChange = rect.left - newLeft
                val heightChange = rect.top - newTop

                val finalWidth: Float
                val finalHeight: Float
                val finalLeft: Float
                val finalTop: Float

                if (widthChange / heightChange > ratio) {
                    finalWidth = (rect.width + widthChange).coerceAtLeast(minSize)
                    finalHeight = finalWidth / ratio
                    finalLeft = rect.right - finalWidth
                    finalTop = rect.bottom - finalHeight
                } else {
                    finalHeight = (rect.height + heightChange).coerceAtLeast(minSize / ratio)
                    finalWidth = finalHeight * ratio
                    finalLeft = rect.right - finalWidth
                    finalTop = rect.bottom - finalHeight
                }

                Rect(
                    finalLeft.coerceIn(videoBounds.left, rect.right - minSize),
                    finalTop.coerceIn(videoBounds.top, rect.bottom - minSize / ratio),
                    rect.right,
                    rect.bottom
                )
            }

            ResizeHandle.TOP_RIGHT -> {
                val newRight =
                    (rect.right + dragAmount.x).coerceIn(rect.left + minSize, videoBounds.right)
                val newTop = (rect.top + dragAmount.y).coerceIn(
                    videoBounds.top,
                    rect.bottom - minSize / ratio
                )

                val widthChange = newRight - rect.right
                val heightChange = rect.top - newTop

                val finalWidth: Float
                val finalHeight: Float
                val finalTop: Float

                if (widthChange / heightChange > ratio) {
                    finalWidth = (rect.width + widthChange).coerceAtLeast(minSize)
                    finalHeight = finalWidth / ratio
                    finalTop = rect.bottom - finalHeight
                } else {
                    finalHeight = (rect.height + heightChange).coerceAtLeast(minSize / ratio)
                    finalWidth = finalHeight * ratio
                    finalTop = rect.bottom - finalHeight
                }

                Rect(
                    rect.left,
                    finalTop.coerceIn(videoBounds.top, rect.bottom - minSize / ratio),
                    (rect.left + finalWidth).coerceAtMost(videoBounds.right),
                    rect.bottom
                )
            }

            ResizeHandle.BOTTOM_LEFT -> {
                val newLeft =
                    (rect.left + dragAmount.x).coerceIn(videoBounds.left, rect.right - minSize)
                val newBottom = (rect.bottom + dragAmount.y).coerceIn(
                    rect.top + minSize / ratio,
                    videoBounds.bottom
                )

                val widthChange = rect.left - newLeft
                val heightChange = newBottom - rect.bottom

                val finalWidth: Float
                val finalHeight: Float
                val finalLeft: Float

                if (widthChange / heightChange > ratio) {
                    finalWidth = (rect.width + widthChange).coerceAtLeast(minSize)
                    finalHeight = finalWidth / ratio
                    finalLeft = rect.right - finalWidth
                } else {
                    finalHeight = (rect.height + heightChange).coerceAtLeast(minSize / ratio)
                    finalWidth = finalHeight * ratio
                    finalLeft = rect.right - finalWidth
                }

                Rect(
                    finalLeft.coerceIn(videoBounds.left, rect.right - minSize),
                    rect.top,
                    rect.right,
                    (rect.top + finalHeight).coerceAtMost(videoBounds.bottom)
                )
            }

            ResizeHandle.BOTTOM_RIGHT -> {
                val newRight =
                    (rect.right + dragAmount.x).coerceIn(rect.left + minSize, videoBounds.right)
                val newBottom = (rect.bottom + dragAmount.y).coerceIn(
                    rect.top + minSize / ratio,
                    videoBounds.bottom
                )

                val widthChange = newRight - rect.right
                val heightChange = newBottom - rect.bottom

                val finalWidth: Float
                val finalHeight: Float

                if (widthChange / heightChange > ratio) {
                    finalWidth = (rect.width + widthChange).coerceAtLeast(minSize)
                    finalHeight = finalWidth / ratio
                } else {
                    finalHeight = (rect.height + heightChange).coerceAtLeast(minSize / ratio)
                    finalWidth = finalHeight * ratio
                }

                Rect(
                    rect.left,
                    rect.top,
                    (rect.left + finalWidth).coerceAtMost(videoBounds.right),
                    (rect.top + finalHeight).coerceAtMost(videoBounds.bottom)
                )
            }
        }
    } else {
        // 自由模式
        when (handle) {
            ResizeHandle.TOP_LEFT -> {
                val newLeft =
                    (rect.left + dragAmount.x).coerceIn(videoBounds.left, rect.right - minSize)
                val newTop =
                    (rect.top + dragAmount.y).coerceIn(videoBounds.top, rect.bottom - minSize)
                Rect(newLeft, newTop, rect.right, rect.bottom)
            }

            ResizeHandle.TOP_RIGHT -> {
                val newRight =
                    (rect.right + dragAmount.x).coerceIn(rect.left + minSize, videoBounds.right)
                val newTop =
                    (rect.top + dragAmount.y).coerceIn(videoBounds.top, rect.bottom - minSize)
                Rect(rect.left, newTop, newRight, rect.bottom)
            }

            ResizeHandle.BOTTOM_LEFT -> {
                val newLeft =
                    (rect.left + dragAmount.x).coerceIn(videoBounds.left, rect.right - minSize)
                val newBottom =
                    (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, videoBounds.bottom)
                Rect(newLeft, rect.top, rect.right, newBottom)
            }

            ResizeHandle.BOTTOM_RIGHT -> {
                val newRight =
                    (rect.right + dragAmount.x).coerceIn(rect.left + minSize, videoBounds.right)
                val newBottom =
                    (rect.bottom + dragAmount.y).coerceIn(rect.top + minSize, videoBounds.bottom)
                Rect(rect.left, rect.top, newRight, newBottom)
            }
        }
    }
}