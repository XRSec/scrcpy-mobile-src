package com.mobile.scrcpy.android.ui.screens

import android.annotation.SuppressLint
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mobile.scrcpy.android.feature.scrcpy.VideoStream
import com.mobile.scrcpy.android.core.media.VideoDecoder
import com.mobile.scrcpy.android.core.media.AudioDecoder
import com.mobile.scrcpy.android.core.media.AudioStream
import com.mobile.scrcpy.android.feature.session.MainViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.BilingualTexts.STATUS_CONNECTING
import com.mobile.scrcpy.android.common.rememberText
import com.mobile.scrcpy.android.core.data.model.getDisplayText
import com.mobile.scrcpy.android.core.data.model.getIcon
import com.mobile.scrcpy.android.ui.components.AutoFloatingMenu

/**
 * 消息项数据类
 */
private data class MessageItem(
    val id: String,
    val icon: String,
    val title: String,
    val subtitle: String = "",
    val error: String? = null
)

/**
 * 消息列表状态管理类
 */
private class MessageListState {
    private val _messages = mutableStateListOf<MessageItem>()
    val messages: List<MessageItem> get() = _messages

    /**
     * 添加消息
     */
    fun addMessage(message: MessageItem) {
        _messages.add(message)
    }

    /**
     * 更新消息（根据 id）
     */
    fun updateMessage(id: String, update: (MessageItem) -> MessageItem) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            _messages[index] = update(_messages[index])
        }
    }

    /**
     * 清空所有消息
     */
    fun clear() {
        _messages.clear()
    }
}

/**
 * 记住消息列表状态
 */
@Composable
private fun rememberMessageListState(): MessageListState {
    return remember { MessageListState() }
}

/**
 * 消息列表组件
 *
 * @param state 消息列表状态
 * @param title 标题文字
 * @param modifier 修饰符
 */
@Composable
private fun MessageList(
    state: MessageListState,
    title: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 始终显示标题
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )

        // 显示消息列表
        state.messages.forEach { message ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.icon,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
                Column {
                    Text(
                        text = message.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (message.subtitle.isNotEmpty()) {
                        Text(
                            text = message.subtitle,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (message.error != null) {
                        Text(
                            text = message.error,
                            color = Color.Red.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * 连接进度显示组件（无窗口，直接显示文字）
 * @param progressText 进度文本
 */
@Composable
private fun ConnectionProgressBox(progressText: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 进度文字靠左上角显示，无背景
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            progressText()
        }

        // 转圈圈在底部居中（距离底部 50dp）
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.3.dp)
        ) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(50.dp),
                strokeWidth = 4.dp
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility", "ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteDisplayScreen(
    viewModel: MainViewModel,
    sessionId: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoStream by viewModel.getVideoStream().collectAsState()
    val audioStream by viewModel.getAudioStream().collectAsState()
    val connectionState by viewModel.getConnectionState().collectAsState()
    val connectionProgress by viewModel.connectionProgress.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val settings by viewModel.settings.collectAsState()  // 收集设置状态

    // 消息列表状态（使用 remember 保持状态，不会因为重组而重置）
    val messageListState = rememberMessageListState()

    // 监听 connectionProgress 变化，同步到消息列表
    LaunchedEffect(connectionProgress) {
        // 如果 connectionProgress 为空（重连时会清空），则清空消息列表
        if (connectionProgress.isEmpty()) {
            messageListState.clear()
        } else {
            connectionProgress.forEach { progress ->
                val messageId = progress.step.name
                val existingMessage = messageListState.messages.find { it.id == messageId }

                val newMessage = MessageItem(
                    id = messageId,
                    icon = progress.status.getIcon(),
                    title = progress.step.getDisplayText(),
                    subtitle = progress.message,
                    error = progress.error
                )

                if (existingMessage == null) {
                    messageListState.addMessage(newMessage)
                } else {
                    messageListState.updateMessage(messageId) { newMessage }
                }
            }
        }
    }

    // 在顶层收集 sessionDataList
    val sessionDataList by viewModel.sessionDataList.collectAsState()
    val sessionData = remember(sessionDataList, sessionId) {
        sessionDataList.find { it.id == sessionId }
    }

    // 键盘输入状态
    var keyboardText by remember { mutableStateOf(TextFieldValue("")) }
    var showKeyboardInput by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    var currentStream by remember { mutableStateOf<VideoStream?>(null) }
    var currentAudioStream by remember { mutableStateOf<AudioStream?>(null) }
    var surfaceHolder by remember { mutableStateOf<SurfaceHolder?>(null) }
    var videoDecoder by remember { mutableStateOf<VideoDecoder?>(null) }
    var audioDecoder by remember { mutableStateOf<AudioDecoder?>(null) }

    // ✅ 监听生命周期事件
    val lifecycleOwner = LocalLifecycleOwner.current
    var lifecycleState by remember { mutableStateOf(Lifecycle.Event.ON_ANY) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycleState = event
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // LogManager.d(LogTags.REMOTE_DISPLAY, "🔄 切换到后台")
                }

                Lifecycle.Event.ON_RESUME -> {
                    scope.launch {
                        try {
                            viewModel.wakeUpScreen()
                        } catch (e: Exception) { }
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isDecoderStarting by remember { mutableStateOf(false) }
    var isAudioDecoderStarting by remember { mutableStateOf(false) }
    var videoAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    // 获取 A 的方向状态
    val configuration = LocalConfiguration.current
    val isALandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // 只监听 A 的旋转，重新计算宽高比
    LaunchedEffect(isALandscape) {
        if (videoWidth > 0 && videoHeight > 0) {
            // A 旋转时，使用当前的视频尺寸重新计算宽高比
            videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()

            val isBLandscape = videoWidth > videoHeight
            val containerAspectRatio =
                configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
            val matchHeightFirst = videoAspectRatio < containerAspectRatio

            LogManager.d(
                LogTags.REMOTE_DISPLAY,
                "🔄 ${BilingualTexts.REMOTE_SCREEN_ROTATION_A.get()}: A${if (isALandscape) BilingualTexts.REMOTE_LANDSCAPE.get() else BilingualTexts.REMOTE_PORTRAIT.get()}, B${if (isBLandscape) BilingualTexts.REMOTE_LANDSCAPE.get() else BilingualTexts.REMOTE_PORTRAIT.get()}, ${BilingualTexts.REMOTE_ASPECT_RATIO.get()}=${videoAspectRatio}, ${BilingualTexts.REMOTE_SCALE_STRATEGY.get()}: ${if (matchHeightFirst) BilingualTexts.REMOTE_FILL_HEIGHT.get() else BilingualTexts.REMOTE_FILL_WIDTH.get()}"
            )
        }
    }

    // 音频解码器启动 - 监听 audioStream 变化
    LaunchedEffect(audioStream) {
        val stream = audioStream

        // 如果 stream 为空，停止解码器
        if (stream == null) {
            if (audioDecoder != null) {
                LogManager.d(LogTags.AUDIO_DECODER, BilingualTexts.REMOTE_AUDIO_STREAM_EMPTY.get())
                audioDecoder?.stop()
                audioDecoder = null
            }
            return@LaunchedEffect
        }

        // 如果已经在处理相同的流，跳过
        if (stream == currentAudioStream) {
            return@LaunchedEffect
        }

        // 如果有旧的解码器，先停止
        if (audioDecoder != null) {
            LogManager.i(LogTags.AUDIO_DECODER, BilingualTexts.REMOTE_AUDIO_STREAM_CHANGED.get())
            audioDecoder?.stop()
            audioDecoder = null
            isAudioDecoderStarting = false
        }

        // 启动新的解码器
        if (!isAudioDecoderStarting) {
            try {
                val codec = stream.codec.lowercase()
                LogManager.d(LogTags.AUDIO_DECODER, "${BilingualTexts.REMOTE_START_AUDIO_DECODER.get()}: codec=$codec")

                // 获取音量设置
                val audioVolume = sessionData?.audioBufferMs?.toFloatOrNull() ?: 1.0f

                // 使用通用 AudioDecoder (支持 opus/raw/aac/flac)
                val decoder = AudioDecoder(volumeScale = audioVolume).apply {
                    // 连接丢失回调
                    onConnectionLost = {
                        LogManager.w(LogTags.AUDIO_DECODER, "⚠️ ${BilingualTexts.REMOTE_AUDIO_CONNECTION_LOST.get()}")
                        scope.launch(Dispatchers.Main) {
                            viewModel.handleConnectionLost()
                        }
                    }
                }
                audioDecoder = decoder

                // 使用 Dispatchers.IO 的独立协程，不受 LaunchedEffect 取消影响
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        decoder.start(stream)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        LogManager.d(LogTags.AUDIO_DECODER, BilingualTexts.REMOTE_AUDIO_DECODER_CANCELLED.get())
                        decoder.stop()
                        if (audioDecoder == decoder) {
                            audioDecoder = null
                        }
                    } catch (e: Exception) {
                        LogManager.e(LogTags.AUDIO_DECODER, "${BilingualTexts.REMOTE_AUDIO_DECODER_FAILED.get()}: ${e.message}", e)
                        decoder.stop()
                        if (audioDecoder == decoder) {
                            audioDecoder = null
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "${BilingualTexts.REMOTE_INIT_AUDIO_DECODER_FAILED.get()}: ${e.message}", e)
                audioDecoder = null
            }
        }
    }

    // ✅ 关键修复：只依赖 videoStream，不依赖 surfaceHolder
    // Surface 的切换通过 DisposableEffect 单独处理
    LaunchedEffect(videoStream) {
        val stream = videoStream

        LogManager.d(
            LogTags.VIDEO_DECODER,
            "🔍 LaunchedEffect 触发: stream=${stream != null}, currentStream=${currentStream != null}, videoDecoder=${videoDecoder != null}"
        )

        // 如果 stream 变化，先停止旧的解码器
        if (stream != currentStream && videoDecoder != null) {
            LogManager.i(LogTags.VIDEO_DECODER, BilingualTexts.REMOTE_VIDEO_STREAM_CHANGED.get())
            videoDecoder?.stop()
            videoDecoder = null
            isDecoderStarting = false
        }

        // 启动解码器的条件：有流、没有正在启动、没有现有解码器
        // Surface 可以为 null（后台启动时使用 dummy Surface）
        if (stream != null && !isDecoderStarting && videoDecoder == null) {
            try {
                // ✅ 获取 Surface（可能为 null，后台启动时使用 dummy）
                val surface = surfaceHolder?.surface

                LogManager.d(
                    LogTags.VIDEO_DECODER,
                    "✅ ${BilingualTexts.REMOTE_PREPARE_VIDEO_DECODER.get()} (surface=${surface != null && surface.isValid})"
                )

                // 获取视频分辨率
                val resolution = viewModel.getVideoResolution().value
                if (resolution == null) {
                    LogManager.e(LogTags.VIDEO_DECODER, BilingualTexts.REMOTE_CANNOT_GET_VIDEO_RESOLUTION.get())
                    return@LaunchedEffect
                }
                val (width, height) = resolution

                LogManager.d(LogTags.VIDEO_DECODER, "📐 ${BilingualTexts.REMOTE_VIDEO_RESOLUTION.get()}: ${width}x${height}")

                // 获取当前会话的视频编码格式
                val videoCodec = sessionData?.videoCodec ?: "h264"

                // 获取缓存的解码器名称（仅在用户选择"默认"编码器时使用）
                val cachedDecoderName = if (sessionData?.videoEncoder.isNullOrBlank()) {
                    // 检查缓存是否有效（7天内）
                    val cacheAge =
                        System.currentTimeMillis() - (sessionData?.codecCacheTimestamp ?: 0L)
                    if (cacheAge < 7 * 24 * 60 * 60 * 1000L) {
                        sessionData?.cachedVideoDecoder
                    } else {
                        null
                    }
                } else {
                    null  // 用户指定了编码器，不使用缓存
                }

                videoDecoder = VideoDecoder(surface, videoCodec, cachedDecoderName).apply {
                    onVideoSizeChanged = { w, h, rotation ->
                        if (w > 0 && h > 0) {
                            LogManager.d(
                                LogTags.VIDEO_DECODER,
                                "🎬 ${BilingualTexts.REMOTE_RECEIVED_VIDEO_SIZE.get()}: ${w}x${h}, rotation=${rotation}°"
                            )

                            // 更新视频尺寸
                            videoWidth = w
                            videoHeight = h

                            // 直接计算宽高比（统一使用 w/h）
                            videoAspectRatio = w.toFloat() / h.toFloat()

                            val isBLandscape = w > h
                            val containerAspectRatio =
                                configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
                            val matchHeightFirst = videoAspectRatio < containerAspectRatio

                            LogManager.d(
                                LogTags.VIDEO_DECODER,
                                "🎯 ${BilingualTexts.REMOTE_SCREEN_ROTATION_B.get()}: A${if (isALandscape) BilingualTexts.REMOTE_LANDSCAPE.get() else BilingualTexts.REMOTE_PORTRAIT.get()}, B${if (isBLandscape) BilingualTexts.REMOTE_LANDSCAPE.get() else BilingualTexts.REMOTE_PORTRAIT.get()}, ${BilingualTexts.REMOTE_ASPECT_RATIO.get()}=${videoAspectRatio}, ${BilingualTexts.REMOTE_SCALE_STRATEGY.get()}: ${if (matchHeightFirst) BilingualTexts.REMOTE_FILL_HEIGHT.get() else BilingualTexts.REMOTE_FILL_WIDTH.get()}"
                            )
                        } else {
                            LogManager.e(LogTags.VIDEO_DECODER, "${BilingualTexts.REMOTE_INVALID_VIDEO_SIZE.get()}: ${w}x${h}")
                        }
                    }

                    // 当解码器选择完成后，保存到会话配置（仅在使用"默认"编码器时）
                    onDecoderSelected = { decoderName ->
                        if (sessionData?.videoEncoder.isNullOrBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    viewModel.updateCodecCache(
                                        sessionId = sessionId,
                                        videoDecoder = decoderName,
                                        audioDecoder = null
                                    )
                                    LogManager.d(
                                        LogTags.VIDEO_DECODER,
                                        "✓ ${BilingualTexts.REMOTE_CACHED_VIDEO_DECODER.get()}: $decoderName"
                                    )
                                } catch (e: Exception) {
                                    LogManager.e(
                                        LogTags.VIDEO_DECODER,
                                        "${BilingualTexts.REMOTE_SAVE_DECODER_CACHE_FAILED.get()}: ${e.message}"
                                    )
                                }
                            }
                        }
                    }

                    // 连接丢失回调 - 触发完整的资源清理和服务停止
                    onConnectionLost = {
                        LogManager.w(LogTags.VIDEO_DECODER, "⚠️ ${BilingualTexts.REMOTE_CONNECTION_LOST_CLEANUP.get()}")
                        scope.launch(Dispatchers.Main) {
                            // 通知 ViewModel 处理连接丢失（完整清理 + 停止服务）
                            viewModel.handleConnectionLost()
                        }
                    }
                }

                // 使用独立协程启动视频解码器（不受 LaunchedEffect 取消影响）
                scope.launch {
                    try {
                        videoDecoder?.start(stream, width, height)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // 协程取消，正常情况（用户退出界面）
                        LogManager.d(LogTags.VIDEO_DECODER, BilingualTexts.REMOTE_DECODER_CANCELLED_UI_CLOSED.get())
                        videoDecoder?.stop()
                        videoDecoder = null
                    } catch (e: Exception) {
                        LogManager.e(LogTags.VIDEO_DECODER, "${BilingualTexts.REMOTE_DECODER_START_FAILED.get()}: ${e.message}", e)
                        videoDecoder?.stop()
                        videoDecoder = null
                    }
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "${BilingualTexts.REMOTE_INIT_DECODER_FAILED.get()}: ${e.message}", e)
                videoDecoder = null
            }
        } else if (stream == null && videoDecoder != null) {
            videoDecoder?.stop()
            videoDecoder = null
        }
    }

    // ✅ 单独处理 Surface 切换（前台/后台）
    DisposableEffect(surfaceHolder, lifecycleState) {
        // 当 Surface 或生命周期变化时，动态切换解码器的输出目标
        val decoder = videoDecoder
        if (decoder != null) {
            when (lifecycleState) {
                Lifecycle.Event.ON_PAUSE -> {
                    // 切换到后台：使用 dummy Surface
                    LogManager.d(LogTags.REMOTE_DISPLAY, "🔄 ${BilingualTexts.REMOTE_SWITCH_TO_BACKGROUND.get()}")
                    decoder.setSurface(null)
                    LogManager.d(LogTags.REMOTE_DISPLAY, "✅ ${BilingualTexts.REMOTE_DECODER_CONTINUE_RUNNING.get()}")
                }

                Lifecycle.Event.ON_RESUME -> {
                    // 恢复到前台：切换回真实 Surface
                    val surface = surfaceHolder?.surface
                    if (surface != null && surface.isValid) {
                        LogManager.d(LogTags.REMOTE_DISPLAY, "🔄 ${BilingualTexts.REMOTE_RESUME_TO_FOREGROUND.get()}")
                        decoder.setSurface(surface)
                        // LogManager.d(LogTags.REMOTE_DISPLAY, "✅ ${BilingualTexts.REMOTE_RESUMED_RENDERING.get()}")
                    } else {
                        LogManager.w(LogTags.REMOTE_DISPLAY, "⚠️ ${BilingualTexts.REMOTE_FOREGROUND_RESUME_INVALID_SURFACE.get()}")
                    }
                }

                else -> {
                    // 其他生命周期事件：检查 Surface 是否有效
                    val surface = surfaceHolder?.surface
                    if (surface != null && surface.isValid) {
                        decoder.setSurface(surface)
                    }
                }
            }
        }

        onDispose { }
    }

    // 清理解码器（只在退出界面时触发）
    DisposableEffect(videoStream, audioStream) {
        onDispose {
            // 异步释放资源，避免阻塞 UI
            scope.launch(Dispatchers.IO) {
                try {
                    LogManager.d(LogTags.REMOTE_DISPLAY, BilingualTexts.REMOTE_START_CLEANUP_RESOURCES.get())

                    // 停止解码器
                    videoDecoder?.stop()
                    audioDecoder?.stop()

                    LogManager.d(LogTags.REMOTE_DISPLAY, BilingualTexts.REMOTE_CLEANUP_COMPLETE.get())
                } catch (e: Exception) {
                    LogManager.e(LogTags.REMOTE_DISPLAY, "${BilingualTexts.REMOTE_CLEANUP_EXCEPTION.get()}: ${e.message}", e)
                }
            }
        }
    }

    // 拦截返回键，传递给远程设备
    BackHandler(enabled = connectionState is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Connected) {
        scope.launch {
            viewModel.sendKeyEvent(4) // KEYCODE_BACK
        }
    }

    // ✅ 使用 Box 替代 Surface，实现真正的全面屏
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 悬浮球（仅在视频流存在且开关开启时显示）
            if (videoStream != null && settings.enableFloatingMenu) {
                AutoFloatingMenu(viewModel = viewModel)
            }

            // 视频显示区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // 始终显示 SurfaceView，避免重连时 Surface 被销毁
                // 比较 A 和 B 的宽高比，决定缩放策略
                val containerAspectRatio =
                    configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

                // 缩放策略：
                // matchHeightConstraintsFirst = false: 优先匹配宽度，填满宽度，高度按比例
                // matchHeightConstraintsFirst = true: 优先匹配高度，填满高度，宽度按比例
                // 
                // 如果 B 的宽高比 > A 的宽高比（B 相对更宽）：
                //   应该 matchHeightConstraintsFirst = false（填满宽度）
                // 如果 B 的宽高比 < A 的宽高比（B 相对更窄）：
                //   应该 matchHeightConstraintsFirst = true（填满高度）
                val matchHeightFirst = videoAspectRatio < containerAspectRatio

                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    surfaceHolder = holder
                                    // LogManager.d(LogTags.REMOTE_DISPLAY, "Surface 已创建")
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                    if (width > 0 && height > 0) {
                                        surfaceHolder = holder
                                        LogManager.d(
                                            LogTags.REMOTE_DISPLAY,
                                            "${BilingualTexts.REMOTE_SURFACE_READY.get()}: ${width}x${height}"
                                        )
                                    }
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    LogManager.d(LogTags.REMOTE_DISPLAY, BilingualTexts.REMOTE_SURFACE_DESTROYED.get())

                                    // ✅ 立即切换到 dummy Surface，防止 MediaCodec 崩溃
                                    videoDecoder?.setSurface(null)
                                    surfaceHolder = null
                                }
                            })

                            // 添加触摸监听
                            setOnTouchListener { view, event ->
                                val resolution = viewModel.getVideoResolution().value
                                if (resolution != null) {
                                    val (deviceWidth, deviceHeight) = resolution

                                    // 计算触摸点在设备屏幕上的坐标
                                    val x = (event.x / view.width * deviceWidth).toInt()
                                    val y = (event.y / view.height * deviceHeight).toInt()

                                    val action = when (event.actionMasked) {
                                        android.view.MotionEvent.ACTION_DOWN -> 0
                                        android.view.MotionEvent.ACTION_UP -> {
                                            view.performClick() // 无障碍支持
                                            1
                                        }

                                        android.view.MotionEvent.ACTION_MOVE -> 2
                                        else -> return@setOnTouchListener false
                                    }

                                    scope.launch {
                                        viewModel.sendTouchEvent(
                                            action = action,
                                            pointerId = event.getPointerId(0).toLong(),
                                            x = x,
                                            y = y,
                                            screenWidth = deviceWidth,
                                            screenHeight = deviceHeight,
                                            pressure = event.pressure
                                        )
                                    }
                                }
                                true
                            }
                        }
                    },
                    update = { view ->
                        // 每次重组时检查 Surface 状态
                        val holder = view.holder
                        val surface = holder.surface
                        if (surface != null && surface.isValid) {
                            if (surfaceHolder == null) {
                                surfaceHolder = holder
                                // 如果解码器已经在运行，切换 Surface 恢复渲染
                                videoDecoder?.setSurface(surface)
                                LogManager.d(
                                    LogTags.REMOTE_DISPLAY,
                                    "✅ update: ${BilingualTexts.REMOTE_SURFACE_RESTORED.get()}"
                                )
                            }
                        } else {
                            LogManager.w(LogTags.REMOTE_DISPLAY, "⚠️ update: ${BilingualTexts.REMOTE_SURFACE_UNAVAILABLE.get()}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(
                            videoAspectRatio,
                            matchHeightConstraintsFirst = matchHeightFirst
                        )
                )

                // 在 SurfaceView 上层显示连接进度或错误信息
                when {
                    connectionState is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Connecting ||
                            connectionState is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Reconnecting ||
                            connectionState !is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Connected &&
                            connectionState !is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Error -> {
                        // 显示连接进度（使用通用消息列表组件）
                        ConnectionProgressBox {
                            MessageList(
                                state = messageListState,
                                title = when (connectionState) {
                                    is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Reconnecting -> "Reconnecting..."
                                    else -> STATUS_CONNECTING.get()
                                }
                            )
                        }
                    }

                    connectionState is com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Error -> {
                        // 显示错误信息和两个按钮（底部居中，距离底部 85dp）
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 32.dp)
                                    .padding(bottom = 85.dp)
                            ) {
                                Text(
                                    text = rememberText(
                                        BilingualTexts.CONNECTION_FAILED_TITLE.chinese,
                                        BilingualTexts.CONNECTION_FAILED_TITLE.english
                                    ),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = (connectionState as com.mobile.scrcpy.android.feature.scrcpy.ConnectionState.Error).message,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            // 直接调用 connectSession，就像用户点击连接按钮一样
                                            viewModel.connectSession(sessionId)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF007AFF)
                                        )
                                    ) {
                                        Text(
                                            rememberText(
                                                BilingualTexts.BUTTON_RECONNECT.chinese,
                                                BilingualTexts.BUTTON_RECONNECT.english
                                            )
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onClose,
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            rememberText(
                                                BilingualTexts.BUTTON_CANCEL_CONNECTION.chinese,
                                                BilingualTexts.BUTTON_CANCEL_CONNECTION.english
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 隐藏的 TextField 用于接收键盘输入
            if (showKeyboardInput) {
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .offset(x = (-1000).dp, y = (-1000).dp) // 移到屏幕外
                ) {
                    var lastTextLength by remember { mutableIntStateOf(0) }

                    BasicTextField(
                        value = keyboardText,
                        onValueChange = { newValue ->
                            val oldText = keyboardText.text
                            val newText = newValue.text
                            val oldLength = lastTextLength

                            // 检测删除操作 - 只在实际删除一个字符时发送
                            if (newText.length < oldText.length && newText.length == oldLength - 1) {
                                scope.launch {
                                    viewModel.sendKeyEvent(67) // KEYCODE_DEL
                                }
                            }
                            // 检测新输入的字符（包括粘贴）
                            else if (newText.length > oldText.length) {
                                // 获取所有新增的字符
                                val newChars = newText.substring(oldText.length)
                                scope.launch {
                                    // 使用 INJECT_TEXT，配合 keyboard=uhid 支持所有语言
                                    viewModel.sendText(newChars)
                                }
                            }

                            lastTextLength = newText.length
                            keyboardText = newValue
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                // 监听快捷键
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.isCtrlPressed) {
                                    when (keyEvent.key) {
                                        Key.A -> {
                                            // Ctrl+A: 全选
                                            scope.launch {
                                                viewModel.sendKeyEvent(
                                                    keyCode = 29, // KEYCODE_A
                                                    action = 0, // ACTION_DOWN
                                                    metaState = 4096 // CTRL
                                                )
                                                kotlinx.coroutines.delay(10)
                                                viewModel.sendKeyEvent(
                                                    keyCode = 29,
                                                    action = 1, // ACTION_UP
                                                    metaState = 4096
                                                )
                                            }
                                            true
                                        }

                                        Key.C -> {
                                            // Ctrl+C: 复制
                                            scope.launch {
                                                viewModel.sendKeyEvent(
                                                    keyCode = 31, // KEYCODE_C
                                                    action = 0,
                                                    metaState = 4096
                                                )
                                                kotlinx.coroutines.delay(10)
                                                viewModel.sendKeyEvent(
                                                    keyCode = 31,
                                                    action = 1,
                                                    metaState = 4096
                                                )
                                            }
                                            true
                                        }

                                        Key.X -> {
                                            // Ctrl+X: 剪切
                                            scope.launch {
                                                viewModel.sendKeyEvent(
                                                    keyCode = 52, // KEYCODE_X
                                                    action = 0,
                                                    metaState = 4096
                                                )
                                                kotlinx.coroutines.delay(10)
                                                viewModel.sendKeyEvent(
                                                    keyCode = 52,
                                                    action = 1,
                                                    metaState = 4096
                                                )
                                            }
                                            true
                                        }

                                        Key.V -> {
                                            // Ctrl+V: 粘贴
                                            scope.launch {
                                                viewModel.sendKeyEvent(
                                                    keyCode = 50, // KEYCODE_V
                                                    action = 0,
                                                    metaState = 4096
                                                )
                                                kotlinx.coroutines.delay(10)
                                                viewModel.sendKeyEvent(
                                                    keyCode = 50,
                                                    action = 1,
                                                    metaState = 4096
                                                )
                                            }
                                            true
                                        }

                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                showKeyboardInput = false
                                keyboardController?.hide()
                                keyboardText = TextFieldValue("") // 清空输入
                                lastTextLength = 0
                            }
                        )
                    )
                }

                // 自动请求焦点并显示键盘
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(200) // 增加延迟，确保 TextField 已渲染
                    try {
                        focusRequester.requestFocus()
                        kotlinx.coroutines.delay(100)
                        keyboardController?.show()
                    } catch (e: Exception) {
                        LogManager.e(LogTags.CONTROL_HANDLER, "${BilingualTexts.REMOTE_FOCUS_REQUEST_FAILED.get()}: ${e.message}")
                    }
                }
            }
        }
    }
}

