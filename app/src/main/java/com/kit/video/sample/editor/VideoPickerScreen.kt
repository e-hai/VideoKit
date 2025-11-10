@file:OptIn(ExperimentalMaterial3Api::class)

package com.kit.video.sample.editor

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

data class VideoPickerInfo(
    val videoUri: Uri,
    val cropInfo: CropInfo
)

// 裁剪信息数据类
data class CropInfo(
    val cropRect: Rect,        // 用户选择的裁剪区域（相对于视频原始尺寸的像素坐标）
    val videoSize: IntSize     // 视频原始尺寸（像素）
)

@Composable
fun VideoEditorScreen(
    modifier: Modifier = Modifier,
) {
    var openFullScreen by remember { mutableStateOf(false) }
    var videoPickerInfo by remember { mutableStateOf<VideoPickerInfo?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (openFullScreen) {
            videoPickerInfo?.let { info ->
                FullScreenPreview(info.videoUri, info.cropInfo)
            }
        } else {
            VideoPickerScreen(onClickFullScreen = { info ->
                videoPickerInfo = info
                openFullScreen = true
            })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerScreen(onClickFullScreen: (info: VideoPickerInfo) -> Unit) {

    var openSheet by remember { mutableStateOf(false) }

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    Column(
        modifier = Modifier.fillMaxSize(),
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
                        modifier = Modifier
                            .height(45.dp)
                            .padding(top = 3.dp),
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
                        openSheet = true
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "确认")
                }

                if (openSheet) {
                    // 显示半屏弹窗展示裁剪后的视频预览
                    val uri = selectedVideoUri ?: return
                    val info = cropInfo ?: return
                    HorizontalVerticalPreview(
                        uri,
                        info,
                        onDismissRequest = { openSheet = false },
                        onClickConfirm = {
                            openSheet = false
                            onClickFullScreen(VideoPickerInfo(uri, info))
                        }
                    )
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

/**
 * 全屏裁剪预览
 * **/
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun FullScreenPreview(videoUri: Uri, cropInfo: CropInfo) {
    Log.d("FullScreenPreview", "videoUri: $videoUri")
    val context = LocalContext.current
    // 跟踪视频是否已准备好
    var videoReady by remember { mutableStateOf(false) }

    // 创建并记住ExoPlayer实例
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ONE

            // 监听视频尺寸变化
            addListener(object : Player.Listener {
                override fun onVideoSizeChanged(size: VideoSize) {
                    videoReady = size.width > 0 && size.height > 0
                }
            })
        }
    }

    // 当视频URI改变时重新加载视频并重置状态
    LaunchedEffect(videoUri) {
        videoReady = false
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                TextureView(context).also {
                    exoPlayer.setVideoTextureView(it)
                }
            },
            update = { view ->
                // 只有当视频准备好且view有尺寸时才应用变换
                if (videoReady && view.width > 0 && view.height > 0) {
                    val cropParameters = calculateVerticalCropParameters(
                        view.width.toFloat(),
                        view.height.toFloat(),
                        cropInfo
                    )

                    val matrix = Matrix()

                    // 应用裁剪变换：先缩放，再平移
                    val pivotX = view.width / 2f
                    val pivotY = view.height / 2f

                    matrix.postScale(cropParameters.scaleX, cropParameters.scaleY, pivotX, pivotY)
                    matrix.postTranslate(cropParameters.translationX, cropParameters.translationY)

                    view.setTransform(matrix)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 横屏/垂屏裁剪预览
 * **/
@Composable
fun HorizontalVerticalPreview(
    videoUri: Uri,
    cropInfo: CropInfo,
    onDismissRequest: () -> Unit,
    onClickConfirm: () -> Unit
) {
    val context = LocalContext.current


    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        containerColor = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(439.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 显示裁剪后的视频预览
            Row(
                modifier = Modifier
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {

                //竖屏
                Box(
                    modifier = Modifier
                        .size(111.dp, 241.dp)
                        .clip(RectangleShape),
                ) {

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

                    AndroidView(
                        factory = { context ->
                            TextureView(context).also {
                                exoPlayer.setVideoTextureView(it)
                            }
                        },
                        update = { view ->

                            val cropParameters = calculateVerticalCropParameters(
                                view.width.toFloat(),
                                view.height.toFloat(),
                                cropInfo
                            )

                            val matrix = Matrix()

                            // 应用裁剪变换：先缩放，再平移
                            val pivotX = view.width / 2f
                            val pivotY = view.height / 2f

                            matrix.postScale(
                                cropParameters.scaleX,
                                cropParameters.scaleY,
                                pivotX,
                                pivotY
                            )
                            matrix.postTranslate(
                                cropParameters.translationX,
                                cropParameters.translationY
                            )

                            view.setTransform(matrix)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                //横屏
                Column {
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

                    Box(
                        modifier = Modifier
                            .size(193.dp, 89.dp)
                            .clip(RectangleShape),
                    ) {
                        AndroidView(
                            factory = { context ->
                                TextureView(context).also {
                                    exoPlayer.setVideoTextureView(it)
                                }
                            },
                            update = { view ->

                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { },
                        modifier = Modifier.size(193.dp, 32.dp),
                        shape = RoundedCornerShape(33.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF4F4F5)
                        )
                    ) {
                        Text(
                            text = "Landscape Mode Settings",
                            fontSize = 10.sp,
                            color = Color(0xFF2B62FF)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onClickConfirm()
                },
                modifier = Modifier
                    .width(343.dp)
                    .height(66.dp),
                shape = RoundedCornerShape(33.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF4F4F5).copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Confirm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(46.dp))
        }
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
fun calculateVerticalCropParameters(
    viewWidth: Float,
    viewHeight: Float,
    cropInfo: CropInfo
): CropParameters {
    Log.d("CropVideoPlayer", "viewWidth: $viewWidth, viewHeight: $viewHeight")

    val cropRatio = cropInfo.cropRect.width / cropInfo.cropRect.height

    var finalCropWidth: Float
    var finalCropHeight: Float

    finalCropWidth = viewWidth
    finalCropHeight = finalCropWidth / cropRatio


    val finalVideoWidth =
        finalCropWidth / (cropInfo.cropRect.width / cropInfo.videoSize.width)
    val finalVideoHeight =
        finalCropHeight / (cropInfo.cropRect.height / cropInfo.videoSize.height)

    Log.d(
        "CropVideoPlayer",
        "finalVideoWidth: $finalVideoWidth, finalVideoHeight: $finalVideoHeight"
    )
    val finalCropLeft =
        cropInfo.cropRect.left / (cropInfo.cropRect.width / finalCropWidth)
    val finalCropTop =
        cropInfo.cropRect.top / (cropInfo.cropRect.height / finalCropHeight)

    Log.d(
        "CropVideoPlayer", "finalCropLeft: $finalCropLeft, finalCropTop: $finalCropTop"
    )
    val finalScaleX = finalVideoWidth / viewWidth
    val finalScaleY = finalVideoHeight / viewHeight

    // 计算偏移量，使裁剪区域居中显示
    val finalCropCenterX = finalCropLeft + finalCropWidth / 2f
    val finalCropCenterY = finalCropTop + finalCropHeight / 2f

    Log.d(
        "CropVideoPlayer",
        "finalCropCenterX: $finalCropCenterX, finalCropCenterY: $finalCropCenterY"
    )

    // 计算视频中心点到裁剪区域中心点的向量，并考虑缩放因素
    val translateX = (finalVideoWidth / 2f - finalCropCenterX)
    val translateY = (finalVideoHeight / 2f - finalCropCenterY)

    return CropParameters(finalScaleX, finalScaleY, translateX, translateY)
}


// 裁剪比例模式枚举
enum class AspectRatioMode(val displayName: String, val ratio: Float?) {
    FREE("自由", null),

    //    RATIO_3_4("3:4", 3f / 4f),
//    RATIO_4_3("4:3", 4f / 3f),
//    RATIO_9_16("9:16", 9f / 16f),
//    RATIO_16_9("16:9", 16f / 9f),
    RATIO_111_241("111:241", 111f / 241f),
    RATIO_193_89("193:89", 193f / 89f),
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
                override fun onVideoSizeChanged(size: VideoSize) {
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
        normalizedLeft * videoSize.width,
        normalizedTop * videoSize.height,
        (normalizedLeft + normalizedWidth) * videoSize.width,
        (normalizedTop + normalizedHeight) * videoSize.height
    )
    Log.d("VideoPickerScreen", "previewRect: $previewRect")
    return CropInfo(previewRect, videoSize)
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
        val maxWidth = actualVideoWidth
        val maxHeight = actualVideoHeight

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
        val width = actualVideoWidth * 0.8f
        val height = actualVideoHeight * 0.8f
        val left = videoOffsetX + (actualVideoWidth - width) / 2f
        val top = videoOffsetY + (actualVideoHeight - height) / 2f

        Rect(left, top, left + width, top + height)
    }
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
                .pointerInput(videoSize, aspectRatioMode) {
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
                                    // 确保coerceIn的参数顺序正确，避免IllegalArgumentException
                                    val maxLeft =
                                        (videoBounds.right - cropRect.width).coerceAtLeast(
                                            videoBounds.left
                                        )
                                    val maxTop =
                                        (videoBounds.bottom - cropRect.height).coerceAtLeast(
                                            videoBounds.top
                                        )

                                    val newLeft = (cropRect.left + dragAmount.x)
                                        .coerceIn(
                                            videoBounds.left,
                                            maxLeft
                                        )
                                    val newTop = (cropRect.top + dragAmount.y)
                                        .coerceIn(
                                            videoBounds.top,
                                            maxTop
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

            // 绘制四个角的控制点 - 改为角形标记
            val cornerLength = 60f
            val cornerWidth = 10f
            // 左上角
            drawLine(
                color = Color.White,
                start = Offset(cropRect.left, cropRect.top + cornerLength),
                end = Offset(cropRect.left, cropRect.top - 5),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.White,
                start = Offset(cropRect.left, cropRect.top),
                end = Offset(cropRect.left + cornerLength, cropRect.top),
                strokeWidth = cornerWidth
            )

            // 右上角
            drawLine(
                color = Color.White,
                start = Offset(cropRect.right - cornerLength, cropRect.top),
                end = Offset(cropRect.right, cropRect.top),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.White,
                start = Offset(cropRect.right, cropRect.top - 5),
                end = Offset(cropRect.right, cropRect.top + cornerLength),
                strokeWidth = cornerWidth
            )

            // 左下角
            drawLine(
                color = Color.White,
                start = Offset(cropRect.left, cropRect.bottom - cornerLength),
                end = Offset(cropRect.left, cropRect.bottom + 5),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.White,
                start = Offset(cropRect.left, cropRect.bottom),
                end = Offset(cropRect.left + cornerLength, cropRect.bottom),
                strokeWidth = cornerWidth
            )

            // 右下角
            drawLine(
                color = Color.White,
                start = Offset(cropRect.right - cornerLength, cropRect.bottom),
                end = Offset(cropRect.right, cropRect.bottom),
                strokeWidth = cornerWidth
            )
            drawLine(
                color = Color.White,
                start = Offset(cropRect.right, cropRect.bottom + 5),
                end = Offset(cropRect.right, cropRect.bottom - cornerLength),
                strokeWidth = cornerWidth
            )

        }
    }
}

enum class ResizeHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

fun getResizeHandle(offset: Offset, rect: Rect): ResizeHandle? {
    val touchRadius = 150f

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
    val minSize = 200f // 减小最小尺寸以避免约束问题
    val ratio = aspectRatioMode.ratio

    // ===== 自由模式 =====
    if (ratio == null) {
        return when (handle) {
            ResizeHandle.TOP_LEFT -> {
                val maxLeft = (rect.right - minSize).coerceAtLeast(videoBounds.left)
                val maxTop = (rect.bottom - minSize).coerceAtLeast(videoBounds.top)

                Rect(
                    left = (rect.left + dragAmount.x).coerceIn(videoBounds.left, maxLeft),
                    top = (rect.top + dragAmount.y).coerceIn(videoBounds.top, maxTop),
                    right = rect.right,
                    bottom = rect.bottom
                )
            }

            ResizeHandle.TOP_RIGHT -> {
                val maxTop = (rect.bottom - minSize).coerceAtLeast(videoBounds.top)
                val minRight = (rect.left + minSize).coerceAtMost(videoBounds.right)

                Rect(
                    left = rect.left,
                    top = (rect.top + dragAmount.y).coerceIn(videoBounds.top, maxTop),
                    right = (rect.right + dragAmount.x).coerceIn(minRight, videoBounds.right),
                    bottom = rect.bottom
                )
            }

            ResizeHandle.BOTTOM_LEFT -> {
                val maxLeft = (rect.right - minSize).coerceAtLeast(videoBounds.left)
                val minBottom = (rect.top + minSize).coerceAtMost(videoBounds.bottom)

                Rect(
                    left = (rect.left + dragAmount.x).coerceIn(videoBounds.left, maxLeft),
                    top = rect.top,
                    right = rect.right,
                    bottom = (rect.bottom + dragAmount.y).coerceIn(minBottom, videoBounds.bottom)
                )
            }

            ResizeHandle.BOTTOM_RIGHT -> {
                val minRight = (rect.left + minSize).coerceAtMost(videoBounds.right)
                val minBottom = (rect.top + minSize).coerceAtMost(videoBounds.bottom)

                Rect(
                    left = rect.left,
                    top = rect.top,
                    right = (rect.right + dragAmount.x).coerceIn(minRight, videoBounds.right),
                    bottom = (rect.bottom + dragAmount.y).coerceIn(minBottom, videoBounds.bottom)
                )
            }
        }
    }

    // ===== 固定比例模式 =====
    return when (handle) {
        ResizeHandle.TOP_LEFT -> {
            val newRight = rect.right
            val newBottom = rect.bottom

            // 确保范围正确
            val maxLeft = (newRight - minSize).coerceAtLeast(videoBounds.left)
            val maxTop = (newBottom - minSize).coerceAtLeast(videoBounds.top)

            var newLeft = (rect.left + dragAmount.x).coerceIn(videoBounds.left, maxLeft)
            var newTop = (rect.top + dragAmount.y).coerceIn(videoBounds.top, maxTop)

            val width = newRight - newLeft
            val height = width / ratio

            if (newBottom - height < videoBounds.top) {
                val heightAvailable = newBottom - videoBounds.top
                val widthFixed = heightAvailable * ratio
                newLeft = newRight - widthFixed
                newTop = videoBounds.top
            } else {
                newTop = newBottom - height
            }

            Rect(newLeft, newTop, newRight, newBottom)
        }

        ResizeHandle.TOP_RIGHT -> {
            val newLeft = rect.left
            val newBottom = rect.bottom

            // 确保范围正确
            val maxTop = (newBottom - minSize).coerceAtLeast(videoBounds.top)
            val minRight = (newLeft + minSize).coerceAtMost(videoBounds.right)

            var newRight = (rect.right + dragAmount.x).coerceIn(minRight, videoBounds.right)
            var newTop = (rect.top + dragAmount.y).coerceIn(videoBounds.top, maxTop)

            val width = newRight - newLeft
            val height = width / ratio

            if (newBottom - height < videoBounds.top) {
                val heightAvailable = newBottom - videoBounds.top
                val widthFixed = heightAvailable * ratio
                newRight = newLeft + widthFixed
                newTop = videoBounds.top
            } else {
                newTop = newBottom - height
            }

            Rect(newLeft, newTop, newRight, newBottom)
        }

        ResizeHandle.BOTTOM_LEFT -> {
            val newRight = rect.right
            val newTop = rect.top

            // 确保范围正确
            val maxLeft = (newRight - minSize).coerceAtLeast(videoBounds.left)
            val minBottom = (newTop + minSize).coerceAtMost(videoBounds.bottom)

            var newLeft = (rect.left + dragAmount.x).coerceIn(videoBounds.left, maxLeft)
            var newBottom = (rect.bottom + dragAmount.y).coerceIn(minBottom, videoBounds.bottom)

            val width = newRight - newLeft
            val height = width / ratio

            if (newTop + height > videoBounds.bottom) {
                val heightAvailable = videoBounds.bottom - newTop
                val widthFixed = heightAvailable * ratio
                newLeft = newRight - widthFixed
                newBottom = videoBounds.bottom
            } else {
                newBottom = newTop + height
            }

            Rect(newLeft, newTop, newRight, newBottom)
        }

        ResizeHandle.BOTTOM_RIGHT -> {
            val newLeft = rect.left
            val newTop = rect.top

            // 确保范围正确
            val minRight = (newLeft + minSize).coerceAtMost(videoBounds.right)
            val minBottom = (newTop + minSize).coerceAtMost(videoBounds.bottom)

            var newRight = (rect.right + dragAmount.x).coerceIn(minRight, videoBounds.right)
            var newBottom = (rect.bottom + dragAmount.y).coerceIn(minBottom, videoBounds.bottom)

            val width = newRight - newLeft
            val height = width / ratio

            if (newTop + height > videoBounds.bottom) {
                val heightAvailable = videoBounds.bottom - newTop
                val widthFixed = heightAvailable * ratio
                newRight = newLeft + widthFixed
                newBottom = videoBounds.bottom
            } else {
                newBottom = newTop + height
            }

            Rect(newLeft, newTop, newRight, newBottom)
        }
    }
}