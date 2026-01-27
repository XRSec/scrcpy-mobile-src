package com.mobile.scrcpy.android.feature.scrcpy

import android.content.Context
import android.content.Intent
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.AppConstants
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.NetworkConstants
import com.mobile.scrcpy.android.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.adb.AdbConnectionManager
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import com.mobile.scrcpy.android.core.adb.AdbBridge
import com.mobile.scrcpy.android.core.data.model.StepStatus
import com.mobile.scrcpy.android.core.data.model.ConnectionStep
import com.mobile.scrcpy.android.core.data.model.ConnectionProgress
import com.mobile.scrcpy.android.core.data.model.getDisplayText
import com.mobile.scrcpy.android.core.data.model.getIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import java.net.Socket
import java.util.Random
import java.io.IOException
import dadb.AdbShellStream
import dadb.AdbShellPacket

/**
 * 视频流接口，用于统一 AdbShellStream 和 ScrcpySocketStream
 */
interface VideoStream : AutoCloseable {
    @Throws(IOException::class)
    fun read(): AdbShellPacket
}

class ScrcpyClient(
    private val context: Context,
    private val adbConnectionManager: AdbConnectionManager
) {

    // 当前使用的设备 ID
    private var currentDeviceId: String? = null

    init {
        // 加载 Native 库
        try {
            System.loadLibrary("scrcpy_adb_bridge")
        } catch (e: UnsatisfiedLinkError) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_NATIVE_LIB_LOAD_FAILED.get()}: ${e.message}", e)
        }
    }

    companion object {
        private const val LOCAL_PORT = 27183

        // PTS 标志位常量（与 scrcpy 服务端一致）
        private const val PACKET_FLAG_CONFIG = 1L shl 63
        private const val PACKET_FLAG_KEY_FRAME = 1L shl 62
        private const val PACKET_PTS_MASK = PACKET_FLAG_KEY_FRAME - 1

        /**
         * 构建 scrcpy-server 基础命令
         * @param params 参数列表（key=value 格式）
         */
        fun buildScrcpyServerCommand(vararg params: String): String {
            val paramsStr = if (params.isNotEmpty()) " ${params.joinToString(" ")}" else ""
            return "CLASSPATH=${AppConstants.SCRCPY_SERVER_PATH} app_process / com.genymobile.scrcpy.Server ${AppConstants.SCRCPY_VERSION}$paramsStr"
        }
    }

    private var videoStream: VideoStream? = null
    private var audioStream: com.mobile.scrcpy.android.core.media.AudioStream? = null
    private var socketServer: java.net.ServerSocket? = null
    private var videoSocket: Socket? = null
    private var audioSocket: Socket? = null
    private var controlSocket: Socket? = null
    private var currentScid: Int? = null
    private var shellStream: AdbShellStream? = null
    private var shellMonitorJob: kotlinx.coroutines.Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 连接进度状态流
    private val _connectionProgress = MutableStateFlow<List<ConnectionProgress>>(emptyList())
    val connectionProgress: StateFlow<List<ConnectionProgress>> = _connectionProgress

    private val _videoStreamState = MutableStateFlow<VideoStream?>(null)
    val videoStreamState: StateFlow<VideoStream?> = _videoStreamState

    private val _audioStreamState = MutableStateFlow<com.mobile.scrcpy.android.core.media.AudioStream?>(null)
    val audioStreamState: StateFlow<com.mobile.scrcpy.android.core.media.AudioStream?> = _audioStreamState

    // 视频分辨率
    private val _videoResolution = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoResolution: StateFlow<Pair<Int, Int>?> = _videoResolution

    // 连接参数缓存（用于重连）
    private var lastMaxSize: Int? = null  // 改为可空类型

    /**
     * 更新连接进度
     */
    private fun updateProgress(
        step: ConnectionStep,
        status: StepStatus,
        message: String = "",
        error: String? = null
    ) {
        val currentList = _connectionProgress.value.toMutableList()

        // 查找是否已存在该步骤
        val existingIndex = currentList.indexOfFirst { it.step == step }
        val progress = ConnectionProgress(step, status, message, error)

        if (existingIndex >= 0) {
            currentList[existingIndex] = progress
        } else {
            currentList.add(progress)
        }

        _connectionProgress.value = currentList
        LogManager.d(LogTags.SCRCPY_CLIENT, "${status.getIcon()} ${step.getDisplayText()}: $message")
    }

    /**
     * 清空连接进度
     */
    private fun clearProgress() {
        _connectionProgress.value = emptyList()
    }    private var lastBitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT
    private var lastMaxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS
    private var lastVideoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC
    private var lastEnableAudio: Boolean = false
    private var lastStayAwake: Boolean = false  // 改为 false，不强制保持唤醒
    private var lastTurnScreenOff: Boolean = false
    private var lastPowerOffOnClose: Boolean = false

    // 重连状态
    private var reconnectAttempts: Int = 0
    private var isReconnecting: Boolean = false

    /**
     * 通过设备 ID 连接 Scrcpy（异步版本，带进度反馈）
     * @param deviceId 设备 ID（格式：host:port）
     * @param skipAdbConnect 是否跳过 ADB 连接步骤（当从 connect() 调用时为 true）
     */
    suspend fun connectByDeviceId(
        deviceId: String,
        maxSize: Int? = null,
        bitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT,
        maxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS,
        videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
        videoEncoder: String = "",
        enableAudio: Boolean = false,
        audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
        audioEncoder: String = "",
        stayAwake: Boolean = false,  // 改为 false，允许设备正常息屏
        turnScreenOff: Boolean = false,
        powerOffOnClose: Boolean = false,
        skipAdbConnect: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (!skipAdbConnect) {
                clearProgress()
                _connectionState.value = ConnectionState.Connecting

                // 步骤 1: 验证 ADB 连接
                updateProgress(
                    ConnectionStep.ADB_CONNECT,
                    StepStatus.RUNNING,
                    BilingualTexts.PROGRESS_VERIFYING_ADB.get()
                )
            } else {
                // 从 connect() 调用，ADB 已连接
                _connectionState.value = ConnectionState.Connecting
            }

            // 获取 ADB 连接
            adbConnectionManager.getConnection(deviceId) ?: throw Exception("Device not connected")

            // 验证 ADB 连接是否真正可用
            val isValid = adbConnectionManager.verifyConnection(deviceId)
            if (!isValid) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ${BilingualTexts.SCRCPY_ADB_CONNECTION_UNAVAILABLE.get()}")

                // ADB 连接已断开，尝试重新连接
                updateProgress(
                    ConnectionStep.ADB_CONNECT,
                    StepStatus.RUNNING,
                    BilingualTexts.PROGRESS_ADB_RECONNECTING.get()
                )

                // 判断连接类型并重连
                if (deviceId.startsWith("usb:")) {
                    // USB 设备：无法自动重连，需要用户重新连接
                    throw Exception(BilingualTexts.ERROR_USB_CONNECTION_LOST.get())
                } else {
                    // TCP 设备：解析 host 和 port 并重连
                    val parts = deviceId.split(":")
                    if (parts.size == 2) {
                        val host = parts[0]
                        val port = parts[1].toIntOrNull() ?: NetworkConstants.DEFAULT_ADB_PORT_INT

                        // 重新连接 ADB
                        val reconnectResult = adbConnectionManager.connectDevice(host, port)
                        if (reconnectResult.isFailure) {
                            throw Exception("${BilingualTexts.ERROR_ADB_RECONNECT_FAILED.get()}: ${reconnectResult.exceptionOrNull()?.message}")
                        }

                        LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_ADB_RECONNECT_SUCCESS.get()}")
                    } else {
                        throw Exception(BilingualTexts.ERROR_INVALID_DEVICE_ID.get() + ": $deviceId")
                    }
                }
            }

            // 标记 ADB 连接成功
            updateProgress(
                ConnectionStep.ADB_CONNECT,
                StepStatus.SUCCESS,
                BilingualTexts.PROGRESS_ADB_NORMAL.get()
            )

            // 重新获取连接（可能已经重连）
            val activeConnection = adbConnectionManager.getConnection(deviceId)
                ?: throw Exception(BilingualTexts.ERROR_CANNOT_GET_ADB_CONNECTION.get())

            // 保存连接参数
            currentDeviceId = deviceId
            lastMaxSize = maxSize
            lastBitRate = bitRate
            lastMaxFps = maxFps
            lastVideoCodec = videoCodec
            lastEnableAudio = enableAudio
            lastStayAwake = stayAwake
            lastTurnScreenOff = turnScreenOff
            lastPowerOffOnClose = powerOffOnClose
            AdbBridge.setConnection(activeConnection)

            // 清理旧资源
            try {
                // 1. 移除旧的 forward
                activeConnection.removeAdbForward(LOCAL_PORT)

                // 2. 杀死旧的 scrcpy-server 进程（如果存在）
                if (currentScid != null) {
                    val oldScidHex = String.format("%08x", currentScid)
                    val killCmd = "pkill -f 'scrcpy.*scid=$oldScidHex' || true"
                    activeConnection.executeShell(killCmd, retryOnFailure = false)
                    LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_CLEANED_OLD_SERVER_PROCESS.get()} (scid=$oldScidHex)")
                }

                delay(200)
            } catch (e: Exception) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_CLEANUP_OLD_RESOURCES_FAILED.get()}: ${e.message}")
            }

            // 生成 SCID
            val scid = generateScid()
            currentScid = scid
            val socketName = "scrcpy_%08x".format(scid)

            // 步骤 2 & 3: 并行执行 Forward 和 Push
            updateProgress(
                ConnectionStep.ADB_FORWARD,
                StepStatus.RUNNING,
                "${BilingualTexts.SCRCPY_PORT_FORWARD.get()} $LOCAL_PORT → $socketName"
            )
            updateProgress(
                ConnectionStep.PUSH_SERVER,
                StepStatus.RUNNING,
                BilingualTexts.PROGRESS_PUSHING_SERVER.get()
            )

            val forwardJob = async {
                activeConnection.setupAdbForward(LOCAL_PORT, socketName).getOrElse {
                    throw Exception("Forward failed")
                }
            }

            val pushJob = async {
                activeConnection.pushScrcpyServer(context).getOrElse {
                    throw Exception("Push failed")
                }
            }

            // 等待 Forward 完成
            try {
                forwardJob.await()
                updateProgress(
                    ConnectionStep.ADB_FORWARD,
                    StepStatus.SUCCESS,
                    BilingualTexts.PROGRESS_PORT_FORWARD.get()
                )
            } catch (e: Exception) {
                updateProgress(
                    ConnectionStep.ADB_FORWARD,
                    StepStatus.FAILED,
                    error = e.message
                )
                throw e
            }

            // 等待 Push 完成
            try {
                pushJob.await()
                updateProgress(
                    ConnectionStep.PUSH_SERVER,
                    StepStatus.SUCCESS,
                    BilingualTexts.PROGRESS_SERVER_PUSHED.get()
                )
            } catch (e: Exception) {
                updateProgress(
                    ConnectionStep.PUSH_SERVER,
                    StepStatus.FAILED,
                    error = e.message
                )
                throw e
            }

            // 步骤 4: 启动 scrcpy-server
            updateProgress(
                ConnectionStep.START_SERVER,
                StepStatus.RUNNING,
                "${BilingualTexts.PROGRESS_STARTING_SERVER.get()} (scid: ${"%08x".format(scid)})"
            )

            val command = buildScrcpyCommand(
                maxSize = maxSize,
                bitRate = bitRate,
                maxFps = maxFps,
                scid = scid,
                videoCodec = videoCodec,
                videoEncoder = videoEncoder,
                enableAudio = enableAudio,
                audioCodec = audioCodec,
                audioEncoder = audioEncoder,
                stayAwake = stayAwake,
                powerOffOnClose = powerOffOnClose
            )

            val stream = activeConnection.openShellStream(command)
                ?: throw Exception("Failed to start server")

            shellStream = stream

            // 启动 shell 输出监控
            shellMonitorJob = CoroutineScope(Dispatchers.IO).launch {
                monitorShellOutput(stream)
            }

            delay(1500) // 等待服务器启动

            updateProgress(
                ConnectionStep.START_SERVER,
                StepStatus.SUCCESS,
                BilingualTexts.PROGRESS_SERVER_STARTED.get()
            )

            // 步骤 5: 连接 Socket
            updateProgress(
                ConnectionStep.CONNECT_SOCKET,
                StepStatus.RUNNING,
                "${BilingualTexts.PROGRESS_CONNECTING_STREAM.get()} (127.0.0.1:$LOCAL_PORT)"
            )

            connectSockets(enableAudio)

            updateProgress(
                ConnectionStep.CONNECT_SOCKET,
                StepStatus.SUCCESS,
                BilingualTexts.PROGRESS_SOCKET_CONNECTED.get()
            )

            // 读取元数据
            readMetadata()

            // 唤醒屏幕（确保每次连接都唤醒）
            wakeUpScreen()

            // 延迟后再次唤醒，确保屏幕真正点亮
//            delay(200)
//            wakeUpScreen()

            // 启动前台服务进行 ADB 保活
            val resolution = _videoResolution.value
            if (resolution != null) {
                startForegroundService(
                    deviceName = deviceId,
                    width = resolution.first,
                    height = resolution.second
                )
            }

            // 完成
            updateProgress(
                ConnectionStep.COMPLETED,
                StepStatus.SUCCESS,
                BilingualTexts.PROGRESS_CONNECTION_ESTABLISHED.get()
            )

            _connectionState.value = ConnectionState.Connected
            Result.success(true)

        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_CONNECTION_FAILED.get()}: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: "Unknown error")
            AdbBridge.clearConnection()
            Result.failure(e)
        }
    }

    /**
     * 监控 shell 输出（增强版，打印所有输出用于调试）
     */
    private fun monitorShellOutput(shellStream: AdbShellStream) {
        try {
            var lineCount = 0
            var lastOutputTime = System.currentTimeMillis()
            var lastHeartbeatTime = System.currentTimeMillis()

            LogManager.d(LogTags.SCRCPY_SERVICE, "========== ${BilingualTexts.SCRCPY_START_MONITOR_OUTPUT.get()} ==========")

            while (true) {
                val currentTime = System.currentTimeMillis()
                val timeSinceLastOutput = currentTime - lastOutputTime
                val timeSinceLastHeartbeat = currentTime - lastHeartbeatTime

                // 每 30 秒打印一次心跳，证明监控线程还活着
                if (timeSinceLastHeartbeat > 30000) {
                    LogManager.d(LogTags.SCRCPY_SERVICE,
                        "💓 ${BilingualTexts.SCRCPY_MONITOR_HEARTBEAT.get()}: ${BilingualTexts.SCRCPY_TOTAL_LINES.get()}=$lineCount, ${BilingualTexts.SCRCPY_SINCE_LAST_OUTPUT.get()}=${timeSinceLastOutput/1000}${BilingualTexts.SCRCPY_NO_OUTPUT_FOR_SECONDS.get()}")
                    lastHeartbeatTime = currentTime
                }

                // 如果超过 10 秒没有输出，记录一次
                if (timeSinceLastOutput > 10000 && lineCount > 0) {
                    LogManager.w(LogTags.SCRCPY_SERVICE,
                        "⏱️ ${BilingualTexts.SCRCPY_NO_OUTPUT_FOR_SECONDS.get()} ${timeSinceLastOutput/1000} ${BilingualTexts.SCRCPY_NO_OUTPUT_FOR_SECONDS.get()} (${BilingualTexts.SCRCPY_TOTAL_LINES.get()}: $lineCount)")
                    lastOutputTime = currentTime
                }

                when (val packet = shellStream.read()) {
                    is AdbShellPacket.StdOut -> {
                        val output = String(packet.payload, Charsets.UTF_8)
                        if (output.isNotBlank()) {
                            lineCount++
                            lastOutputTime = currentTime

                            // 调试期间：打印所有输出（不限制行数）
                            LogManager.d(LogTags.SCRCPY_SERVICE, output)

                            // 特别关注的关键信息
                            when {
                                output.contains("Device:") ->
                                    LogManager.i(LogTags.SCRCPY_SERVICE, "🔍 $output")
                                output.contains("ERROR") || output.contains("error") ->
                                    LogManager.e(LogTags.SCRCPY_SERVICE, "❌ $output")
                                output.contains("WARN") || output.contains("warn") ->
                                    LogManager.w(LogTags.SCRCPY_SERVICE, "⚠️ $output")
                                output.contains("encoder") || output.contains("codec") ->
                                    LogManager.i(LogTags.SCRCPY_SERVICE, output)
                                output.contains("Display") || output.contains("display") ->
                                    LogManager.i(LogTags.SCRCPY_SERVICE, "📺 $output")
                                output.contains("screen") || output.contains("Screen") ->
                                    LogManager.i(LogTags.SCRCPY_SERVICE, "🖥️ $output")
                                output.contains("socket") || output.contains("Socket") ->
                                    LogManager.w(LogTags.SCRCPY_SERVICE, "🔌 $output")
                            }
                        }
                    }
                    is AdbShellPacket.StdError -> {
                        val error = String(packet.payload, Charsets.UTF_8)
                        if (error.isNotBlank()) {
                            lineCount++
                            lastOutputTime = currentTime
                            LogManager.e(LogTags.SCRCPY_SERVICE, "ERROR $error")
                        }
                    }
                    is AdbShellPacket.Exit -> {
                        val exitCode = packet.payload.firstOrNull()?.toInt() ?: -1
                        LogManager.d(LogTags.SCRCPY_SERVICE,
                            "Exit: exitCode=$exitCode, ${BilingualTexts.SCRCPY_TOTAL_LINES.get()}: $lineCount")

                        // 进程退出（无论正常还是异常），都需要通知主线程
                        if (exitCode == 0) {
                            LogManager.w(LogTags.SCRCPY_SERVICE,
                                "⚠️ ${BilingualTexts.SCRCPY_NORMAL_EXIT.get()}")
                        } else {
                            LogManager.e(LogTags.SCRCPY_SERVICE,
                                "❌ ${BilingualTexts.SCRCPY_ABNORMAL_EXIT.get()}: exitCode=$exitCode")
                        }
                        updateConnectionStateOnError(BilingualTexts.SCRCPY_EXITED.get())
                        break
                    }
                }
            }

            LogManager.d(LogTags.SCRCPY_SERVICE, "========== ${BilingualTexts.SCRCPY_MONITOR_OUTPUT_END.get()} ==========")
        } catch (e: Exception) {
            LogManager.d(LogTags.SCRCPY_SERVICE, "Monitor ended: ${e.message}")
        }
    }

    /**
     * 连接所有 Socket
     */
    private suspend fun connectSockets(enableAudio: Boolean) = withContext(Dispatchers.IO) {
        // 视频流
        val vSocket = Socket()
        vSocket.connect(java.net.InetSocketAddress("127.0.0.1", LOCAL_PORT), 5000)
        vSocket.soTimeout = 2000  // 2秒超时，快速响应
        vSocket.tcpNoDelay = true  // ✅ 禁用 Nagle 算法，降低延迟
        videoSocket = vSocket

        // 音频流（如果启用）
        if (enableAudio) {
            try {
                val aSocket = Socket()
                aSocket.connect(java.net.InetSocketAddress("127.0.0.1", LOCAL_PORT), 5000)
                aSocket.soTimeout = 10000
                aSocket.tcpNoDelay = true  // ✅ 禁用 Nagle 算法，降低延迟
                audioSocket = aSocket
            } catch (e: Exception) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "Audio socket failed: ${e.message}")
                audioSocket?.close()
                audioSocket = null
            }
        }

        // 控制流
        try {
            val cSocket = Socket()
            cSocket.connect(java.net.InetSocketAddress("127.0.0.1", LOCAL_PORT), 5000)
            cSocket.soTimeout = 0
            cSocket.tcpNoDelay = true
            controlSocket = cSocket
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "Control socket failed: ${e.message}")
            controlSocket?.close()
            controlSocket = null
        }
    }

    /**
     * 读取视频流元数据
     */
    private suspend fun readMetadata() = withContext(Dispatchers.IO) {
        val vSocket = videoSocket ?: throw Exception("Video socket not connected")
        
        // ✅ 优化：使用大缓冲区（256KB），减少系统调用次数
        val dataInputStream = java.io.DataInputStream(
            java.io.BufferedInputStream(vSocket.inputStream, 256 * 1024)
        )

        // 等待数据可用（增加等待时间和重试次数）
        var waitCount = 100  // 100 * 100ms = 10s
        LogManager.d(LogTags.SCRCPY_CLIENT, "📦 ${BilingualTexts.SCRCPY_WAIT_METADATA.get()}")
        while (dataInputStream.available() <= 0 && waitCount-- > 0) {
            delay(100)
        }

        if (dataInputStream.available() <= 0) {
            throw Exception("No metadata available after 10s timeout")
        }

        LogManager.d(LogTags.SCRCPY_CLIENT, "📦 ${BilingualTexts.SCRCPY_START_READ_METADATA.get()}: ${dataInputStream.available()}")

        try {
            // 读取 dummy byte
            val dummyByte = dataInputStream.readByte()
            LogManager.d(LogTags.SCRCPY_CLIENT, "📦 Dummy byte: $dummyByte")

            // 读取设备名称
            val deviceNameBytes = ByteArray(64)
            dataInputStream.readFully(deviceNameBytes)
            val deviceName = String(deviceNameBytes).trim('\u0000')
            LogManager.d(LogTags.SCRCPY_CLIENT, "📦 ${BilingualTexts.SCRCPY_DEVICE_NAME.get()}: $deviceName")

            // 读取 codec metadata
            val codecBytes = ByteArray(12)
            dataInputStream.readFully(codecBytes)

            val codecId = ((codecBytes[0].toInt() and 0xFF) shl 24) or
                    ((codecBytes[1].toInt() and 0xFF) shl 16) or
                    ((codecBytes[2].toInt() and 0xFF) shl 8) or
                    (codecBytes[3].toInt() and 0xFF)

            val deviceWidth = ((codecBytes[4].toInt() and 0xFF) shl 24) or
                    ((codecBytes[5].toInt() and 0xFF) shl 16) or
                    ((codecBytes[6].toInt() and 0xFF) shl 8) or
                    (codecBytes[7].toInt() and 0xFF)

            val deviceHeight = ((codecBytes[8].toInt() and 0xFF) shl 24) or
                    ((codecBytes[9].toInt() and 0xFF) shl 16) or
                    ((codecBytes[10].toInt() and 0xFF) shl 8) or
                    (codecBytes[11].toInt() and 0xFF)

            LogManager.d(LogTags.SCRCPY_CLIENT, "📦 ${BilingualTexts.SCRCPY_CODEC_ID.get()}: $codecId, ${BilingualTexts.SCRCPY_RESOLUTION.get()}: ${deviceWidth}x${deviceHeight}")

            if (deviceWidth <= 0 || deviceHeight <= 0 || deviceWidth > 4096 || deviceHeight > 4096) {
                throw IllegalStateException("Invalid resolution: ${deviceWidth}x${deviceHeight}")
            }

            _videoResolution.value = Pair(deviceWidth, deviceHeight)

            // 创建视频流包装器
            _videoStreamState.value = object : VideoStream {
                private var lastReadTime = System.currentTimeMillis()
                private var consecutiveTimeouts = 0
                private var totalPackets = 0

                override fun read(): AdbShellPacket {
                    try {
                        val startTime = System.currentTimeMillis()
                        val timeSinceLastRead = startTime - lastReadTime

                        // 记录读取间隔（前10次和每50次打印一次）
                        if (totalPackets < 10 || totalPackets % 50 == 0) {
                            LogManager.d(LogTags.SCRCPY_CLIENT,
                                "📊 ${BilingualTexts.SCRCPY_VIDEO_PACKET.get()} #$totalPackets: ${BilingualTexts.SCRCPY_SINCE_LAST_READ.get()} ${timeSinceLastRead}ms, ${BilingualTexts.SCRCPY_CONSECUTIVE_TIMEOUTS.get()}: $consecutiveTimeouts")
                        }

                        // ✅ 正确顺序：先读 PTS (8 bytes)，再读 Size (4 bytes)
                        val ptsAndFlags = dataInputStream.readLong()  // uint64 pts (包含标志位, big-endian)
                        val packetSize = dataInputStream.readInt()    // uint32 size (big-endian)

                        // 读取成功，重置超时计数
                        consecutiveTimeouts = 0
                        lastReadTime = System.currentTimeMillis()
                        totalPackets++

                        // val readDuration = lastReadTime - startTime
                        // if (readDuration > 1000) {
                            // LogManager.w(LogTags.SCRCPY_CLIENT, "⚠️ 读取耗时过长: ${readDuration}ms (包 #$totalPackets)")
                        // }

                        // 验证 packetSize 合法性
                        if (packetSize < 0 || packetSize > 10 * 1024 * 1024) { // 最大 10MB
                            LogManager.e(LogTags.SCRCPY_CLIENT,
                                "❌ ${BilingualTexts.SCRCPY_INVALID_PACKET_SIZE.get()}: $packetSize, pts=$ptsAndFlags (${BilingualTexts.SCRCPY_VIDEO_PACKET.get()} #$totalPackets)")
                            updateConnectionStateOnError(BilingualTexts.SCRCPY_PACKET_SIZE_ABNORMAL.get())
                            throw IllegalStateException("${BilingualTexts.SCRCPY_INVALID_PACKET_SIZE.get()}: $packetSize (${BilingualTexts.SCRCPY_DATA_STREAM_OUT_OF_SYNC.get()})")
                        }

                        val data = ByteArray(packetSize)
                        dataInputStream.readFully(data)

                        // 打印数据包详情（前5个包）
                        if (totalPackets <= 5) {
                            val preview = data.take(16).joinToString(" ") { "%02X".format(it) }
                            LogManager.d(LogTags.SCRCPY_CLIENT,
                                "📦 包 #$totalPackets: size=$packetSize, pts=$ptsAndFlags, data=$preview...")
                        }

                        return AdbShellPacket.StdOut(data)
                    } catch (e: java.net.SocketTimeoutException) {
                        consecutiveTimeouts++
                        val timeSinceLastRead = System.currentTimeMillis() - lastReadTime

                        // 超时：记录详细信息（每 5 次打印一次，避免日志刷屏）
                        if (consecutiveTimeouts % 5 == 1) {
                            LogManager.w(LogTags.SCRCPY_CLIENT,
                                "⏱️ ${BilingualTexts.SCRCPY_VIDEO_STREAM_TIMEOUT.get()} #$consecutiveTimeouts: ${BilingualTexts.SCRCPY_WAITED.get()} ${timeSinceLastRead}ms, ${BilingualTexts.SCRCPY_TOTAL_PACKETS.get()}: $totalPackets")
                        }

                        // 检查控制 socket 是否还活着
                        val controlSocketAlive = controlSocket?.let {
                            !it.isClosed && it.isConnected
                        } ?: false

                        if (controlSocketAlive) {
                            // 控制流正常 → 只是设备息屏或网络慢，立即重试
                            if (consecutiveTimeouts % 5 == 1) {
                                LogManager.d(LogTags.SCRCPY_CLIENT, "💤 ${BilingualTexts.SCRCPY_DEVICE_MAY_SLEEP.get()}")
                            }
                            // ✅ 立即重试，不返回空包（避免解码器空转）
                            return read()  // 递归调用，立即重试
                        } else {
                            // 控制流也断开 → 真正的连接断开
                            LogManager.e(LogTags.SCRCPY_CLIENT,
                                "❌ ${BilingualTexts.SCRCPY_CONTROL_STREAM_DISCONNECTED.get()}")
                            updateConnectionStateOnError(BilingualTexts.SCRCPY_CONNECTION_DISCONNECTED.get())
                            throw e
                        }
                    } catch (e: java.io.EOFException) {
                        // 流结束
                        LogManager.d(LogTags.SCRCPY_CLIENT,
                            "📭 ${BilingualTexts.SCRCPY_VIDEO_STREAM_CLOSED.get()} (${BilingualTexts.SCRCPY_TOTAL_RECEIVED_PACKETS.get()} $totalPackets ${BilingualTexts.SCRCPY_PACKETS.get()})")
                        updateConnectionStateOnError(BilingualTexts.SCRCPY_VIDEO_STREAM_CLOSED.get())
                        throw e
                    } catch (e: IOException) {
                        // 其他 IO 错误
                        LogManager.e(LogTags.SCRCPY_CLIENT,
                            "❌ ${BilingualTexts.SCRCPY_VIDEO_STREAM_READ_ERROR.get()}: ${e.message} (${BilingualTexts.SCRCPY_VIDEO_PACKET.get()} #$totalPackets)")
                        updateConnectionStateOnError("${BilingualTexts.SCRCPY_VIDEO_STREAM_READ_ERROR.get()}: ${e.message}")
                        throw e
                    }
                }
                override fun close() {
                    LogManager.d(LogTags.SCRCPY_CLIENT,
                        "🔒 ${BilingualTexts.SCRCPY_CLOSE_VIDEO_STREAM.get()} (${BilingualTexts.SCRCPY_TOTAL_RECEIVED_PACKETS.get()} $totalPackets ${BilingualTexts.SCRCPY_PACKETS.get()})")
                    videoSocket?.close()
                }
            }

            LogManager.d(LogTags.SCRCPY_CLIENT, "✅ ${BilingualTexts.SCRCPY_METADATA_READ_COMPLETE.get()}")

        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "❌ ${BilingualTexts.SCRCPY_METADATA_READ_FAILED.get()}: ${e.message}", e)
            throw Exception("Failed to read metadata: ${e.message}", e)
        }
    }

    /**
     * * 唤醒屏幕（增强版）
     */
    suspend fun wakeUpScreen(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // ✅ 检查连接状态
            if (controlSocket == null || controlSocket?.isClosed == true) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "⚠️ ${BilingualTexts.ERROR_CONTROL_NOT_READY.get()}")
                return@withContext Result.failure(Exception(BilingualTexts.ERROR_CONTROL_NOT_READY.get()))
            }

            // 方法1: 发送 KEYCODE_WAKEUP (224)
            sendKeyEvent(224) // KEYCODE_WAKEUP
            delay(50)

            LogManager.d(LogTags.SCRCPY_CLIENT, "✅ ${BilingualTexts.SCRCPY_SCREEN_WAKE_SIGNAL_SENT.get()}")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_WAKE_SCREEN_FAILED.get()}: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 直接通过 host:port 连接（会自动创建 ADB 连接）
     */
    suspend fun connect(
        host: String,
        port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT,
        maxSize: Int? = null,  // 改为可空类型
        bitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT,
        maxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS,
        videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
        videoEncoder: String = "",
        enableAudio: Boolean = false,
        audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
        audioEncoder: String = "",
        stayAwake: Boolean = false,  // 改为 false，允许设备正常息屏
        turnScreenOff: Boolean = false,
        powerOffOnClose: Boolean = false
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 清空之前的进度记录
            clearProgress()

            // 立即显示第一步，让对话框立即出现
            _connectionState.value = ConnectionState.Connecting
            
            // 判断是 USB 还是 TCP 连接
            val isUsbConnection = host.startsWith("usb:")
            val deviceId: String
            
            if (isUsbConnection) {
                // USB 连接：host 格式为 "usb:序列号"
                deviceId = host
                updateProgress(
                    ConnectionStep.ADB_CONNECT,
                    StepStatus.RUNNING,
                    "${BilingualTexts.PROGRESS_VERIFYING_ADB.get()} ($deviceId)"
                )
                
                // USB 设备应该已经在设备管理中连接，直接验证连接
                val connection = adbConnectionManager.getConnection(deviceId)
                if (connection == null) {
                    val errorMsg = "${BilingualTexts.USB_CONNECT_FAILED.get()}: ${BilingualTexts.ADB_DEVICE_NOT_CONNECTED.get()}"
                    updateProgress(
                        ConnectionStep.ADB_CONNECT,
                        StepStatus.FAILED,
                        error = errorMsg
                    )
                    _connectionState.value = ConnectionState.Error(errorMsg)
                    return@withContext Result.failure(Exception(errorMsg))
                }
            } else {
                // TCP 连接：正常的 host:port
                deviceId = "$host:$port"
                updateProgress(
                    ConnectionStep.ADB_CONNECT,
                    StepStatus.RUNNING,
                    "${BilingualTexts.PROGRESS_VERIFYING_ADB.get()} ($host:$port)"
                )
                
                // 先连接 ADB
                val connectResult = adbConnectionManager.connectDevice(host, port)
                if (connectResult.isFailure) {
                    val errorMsg = connectResult.exceptionOrNull()?.message ?: BilingualTexts.ERROR_CONNECTION_FAILED.get()
                    updateProgress(
                        ConnectionStep.ADB_CONNECT,
                        StepStatus.FAILED,
                        error = errorMsg
                    )
                    _connectionState.value = ConnectionState.Error(errorMsg)
                    return@withContext Result.failure(connectResult.exceptionOrNull() ?: Exception(errorMsg))
                }
            }

            // 再连接 Scrcpy（内部会更新后续步骤的进度）
            val result = connectByDeviceId(
                deviceId = deviceId,
                maxSize = maxSize,
                bitRate = bitRate,
                maxFps = maxFps,
                videoCodec = videoCodec,
                videoEncoder = videoEncoder,
                enableAudio = enableAudio,
                audioCodec = audioCodec,
                audioEncoder = audioEncoder,
                stayAwake = stayAwake,
                turnScreenOff = turnScreenOff,
                powerOffOnClose = powerOffOnClose,
                skipAdbConnect = true  // 跳过 ADB 连接步骤，因为已经在上面完成
            )

            return@withContext result
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_CONNECTION_FAILED_DETAIL.get()}: ${e.message}", e)
            _connectionState.value = ConnectionState.Error(e.message ?: BilingualTexts.ERROR_CONNECTION_FAILED.get())
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            _connectionState.value = ConnectionState.Disconnecting

            // 重置重连状态
            reconnectAttempts = 0
            isReconnecting = false

            // 1. 停止 shell 监控协程
            shellMonitorJob?.cancel()
            shellMonitorJob = null

            // 2. 关闭 shell stream
            try {
                shellStream?.close()
                shellStream = null
                LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_CLOSED_SHELL_STREAM.get()}")
            } catch (e: Exception) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_CLOSE_SHELL_STREAM_FAILED.get()}: ${e.message}")
            }

            // 3. 关闭 scrcpy 相关资源
            videoStream?.close()
            videoStream = null
            audioStream?.close()
            audioStream = null
            videoSocket?.close()
            videoSocket = null
            audioSocket?.close()
            audioSocket = null
            controlSocket?.close()
            controlSocket = null
            socketServer?.close()
            socketServer = null
            _videoStreamState.value = null
            _audioStreamState.value = null

            // 4. 清理 forward
            if (currentDeviceId != null) {
                val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                if (connection != null) {
                    try {
                        connection.removeAdbForward(LOCAL_PORT)
                        LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_REMOVED_ADB_FORWARD.get()}")
                    } catch (e: Exception) {
                        LogManager.w(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_REMOVE_FORWARD_FAILED.get()}: ${e.message}")
                    }
                }
            }

            // 5. 杀死 scrcpy-server 进程（如果存在）
            if (currentDeviceId != null && currentScid != null) {
                val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                if (connection != null) {
                    try {
                        val scidHex = String.format("%08x", currentScid)
                        // 查找并杀死 scrcpy-server 进程
                        val killCmd = "pkill -f 'scrcpy.*scid=$scidHex' || killall -9 app_process"
                        connection.executeShell(killCmd, retryOnFailure = false)
                        LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_TERMINATED_SERVER_PROCESS.get()} (scid=$scidHex)")
                    } catch (e: Exception) {
                        LogManager.w(LogTags.SCRCPY_CLIENT, "${BilingualTexts.SCRCPY_TERMINATE_SERVER_FAILED.get()}: ${e.message}")
                    }
                }
            }

            // 6. 清理状态数据
            clearProgress()  // 清空连接进度
            _videoResolution.value = null  // 清空视频分辨率

            // 注意：不清理 currentDeviceId 和 ADB 连接，保持保活
            currentScid = null
            _connectionState.value = ConnectionState.Disconnected

            LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ${BilingualTexts.SCRCPY_DISCONNECTED_ADB_KEPT.get()}")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "断开连接失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun buildScrcpyCommand(
        maxSize: Int?,  // 改为可空类型
        bitRate: Int,
        maxFps: Int,
        scid: Int,
        videoCodec: String = "h264",
        videoEncoder: String = "",
        enableAudio: Boolean = false,
        audioCodec: String = "aac",
        audioEncoder: String = "",
        audioBufferMs: String = "",
        stayAwake: Boolean = false,  // 改为 false，允许设备正常息屏
        turnScreenOff: Boolean = false,
        powerOffOnClose: Boolean = false
    ): String {
        val scidHex = String.format("%08x", scid)
        val params = mutableListOf(
            "scid=$scidHex",
            "log_level=debug"
        )

        // 只有当 maxSize 不为 null 时才添加 max_size 参数
        if (maxSize != null && maxSize > 0) {
            params.add("max_size=$maxSize")
        }

        params.addAll(listOf(
            "video_bit_rate=$bitRate",
            "max_fps=$maxFps",
            "video_codec=$videoCodec",
            "stay_awake=$stayAwake",
            "power_off_on_close=$powerOffOnClose",
            "tunnel_forward=true"
        ))

        if (videoEncoder.isNotBlank()) {
            params.add("video_encoder=$videoEncoder")
        }

        // 音频参数
        if (enableAudio) {
            params.add("audio_codec=$audioCodec")
            params.add("audio_bit_rate=128000")  // 128kbps
            if (audioEncoder.isNotBlank()) {
                params.add("audio_encoder=$audioEncoder")
            }
        } else {
            params.add("audio=false")
        }

        // 低延迟编码参数
        params.add("video_codec_options=profile=1,level=52,intra-refresh-period=0")

        return buildScrcpyServerCommand(*params.toTypedArray())
    }

    /**
     * 生成随机 SCID（Socket Connection ID）
     * SCID 是 31 位随机数，格式化为 8 位十六进制
     */
    private fun generateScid(): Int {
        val random = Random()
        // 31 位随机数（0x7FFFFFFF 是最大 31 位值）
        return random.nextInt(0x7FFFFFFF)
    }


    /**
     * 发送触摸事件（支持多指触摸）
     * 按照 scrcpy 标准控制消息格式
     */
    suspend fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (currentDeviceId == null) {
                return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_NOT_CONNECTED.get()))
            }

            // scrcpy 控制消息格式：
            // SC_CONTROL_MSG_TYPE_INJECT_TOUCH_EVENT = 2
            val buffer = ByteArray(32)
            var offset = 0

            buffer[offset++] = 2 // Type
            buffer[offset++] = action.toByte()
            writeLong(buffer, offset, pointerId)
            offset += 8
            writeInt(buffer, offset, x)
            offset += 4
            writeInt(buffer, offset, y)
            offset += 4
            writeShort(buffer, offset, screenWidth)
            offset += 2
            writeShort(buffer, offset, screenHeight)
            offset += 2
            val pressureInt = (pressure * 0xFFFF).toInt().coerceIn(0, 0xFFFF)
            writeShort(buffer, offset, pressureInt)
            offset += 2
            writeInt(buffer, offset, 0) // action_button
            offset += 4
            writeInt(buffer, offset, 0) // buttons

            // 优先使用控制 socket
            val socket = controlSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                try {
                    val outputStream = socket.getOutputStream()
                    outputStream.write(buffer)
                    outputStream.flush()
                    return@withContext Result.success(true)
                } catch (e: Exception) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "控制 socket 发送失败，回退到 ADB: ${e.message}")
                    try {
                        socket.close()
                    } catch (ignored: Exception) {
                    }
                    controlSocket = null
                }
            }

            // 回退到 ADB shell
            val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                ?: return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

            val hexString = buffer.joinToString("") { "%02x".format(it) }
            val command = "echo -n '$hexString' | xxd -r -p | nc 127.0.0.1 $LOCAL_PORT"

            val result = connection.executeShell(command)
            if (result.isSuccess) {
                Result.success(true)
            } else {
                Result.failure(Exception(BilingualTexts.ERROR_SEND_FAILED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送触摸事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 将 int 值写入字节数组（大端序）
     */
    private fun writeInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (value and 0xFF).toByte()
    }

    /**
     * 将 long 值写入字节数组（大端序）
     */
    private fun writeLong(buffer: ByteArray, offset: Int, value: Long) {
        buffer[offset] = ((value shr 56) and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 48) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 40) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 32) and 0xFF).toByte()
        buffer[offset + 4] = ((value shr 24) and 0xFF).toByte()
        buffer[offset + 5] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 6] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 7] = (value and 0xFF).toByte()
    }

    /**
     * 将 short 值写入字节数组（大端序）
     */
    private fun writeShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 1] = (value and 0xFF).toByte()
    }

    /**
     * 从字节数组读取 int 值（大端序）
     */
    private fun bytesToInt(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)
    }

    suspend fun sendKeyEvent(
        keyCode: Int,
        action: Int = -1, // -1 表示发送完整的按下+释放事件
        repeat: Int = 0,
        metaState: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (currentDeviceId == null) {
            return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_NOT_CONNECTED.get()))
        }

        // ✅ 检查控制连接是否就绪
        if (controlSocket == null || controlSocket?.isClosed == true) {
            return@withContext Result.failure(Exception(BilingualTexts.ERROR_CONTROL_NOT_READY.get()))
        }

        return@withContext try {
            // 如果 action = -1，发送完整的按键事件（按下+释放）
            if (action == -1) {
                // 发送按下事件
                sendSingleKeyEvent(keyCode, 0, repeat, metaState)
                delay(10) // 短暂延迟
                // 发送释放事件
                sendSingleKeyEvent(keyCode, 1, repeat, metaState)
                return@withContext Result.success(true)
            } else {
                // 发送单个事件
                return@withContext sendSingleKeyEvent(keyCode, action, repeat, metaState)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun sendSingleKeyEvent(
        keyCode: Int,
        action: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // scrcpy 控制消息格式：SC_CONTROL_MSG_TYPE_INJECT_KEYCODE = 0
            val buffer = ByteArray(14)
            var offset = 0

            buffer[offset++] = 0 // Type
            buffer[offset++] = action.toByte()
            writeInt(buffer, offset, keyCode)
            offset += 4
            writeInt(buffer, offset, repeat)
            offset += 4
            writeInt(buffer, offset, metaState)

            // 优先使用控制 socket
            val socket = controlSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                try {
                    val outputStream = socket.getOutputStream()
                    outputStream.write(buffer)
                    outputStream.flush()
                    LogManager.d(LogTags.SCRCPY_CLIENT, "按键事件已发送: keyCode=$keyCode, action=$action")
                    return@withContext Result.success(true)
                } catch (e: Exception) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "控制 socket 发送失败，回退到 ADB: ${e.message}")
                    // 关闭损坏的 socket
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                    controlSocket = null
                }
            }

            // 回退到 ADB shell
            val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                ?: return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

            val hexString = buffer.joinToString("") { "%02x".format(it) }
            val command = "echo -n '$hexString' | xxd -r -p | nc 127.0.0.1 $LOCAL_PORT"

            val result = connection.executeShell(command)
            if (result.isSuccess) {
                LogManager.d(LogTags.SCRCPY_CLIENT, "按键事件已发送: keyCode=$keyCode, action=$action")
                Result.success(true)
            } else {
                LogManager.e(LogTags.SCRCPY_CLIENT, "按键事件发送失败")
                Result.failure(Exception(BilingualTexts.ERROR_SEND_FAILED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送按键事件失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun sendText(text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (currentDeviceId == null) {
            return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_NOT_CONNECTED.get()))
        }

        LogManager.d(LogTags.SCRCPY_CLIENT, "发送文本: '$text'")

        return@withContext try {
            // scrcpy 控制消息格式：SC_CONTROL_MSG_TYPE_INJECT_TEXT = 1
            val textBytes = text.toByteArray(Charsets.UTF_8)

            if (textBytes.size > 300) {
                return@withContext Result.failure(Exception(BilingualTexts.ERROR_TEXT_TOO_LONG.get()))
            }

            val buffer = ByteArray(5 + textBytes.size)
            var offset = 0

            buffer[offset++] = 1 // Type
            writeInt(buffer, offset, textBytes.size)
            offset += 4
            System.arraycopy(textBytes, 0, buffer, offset, textBytes.size)

            // 优先使用控制 socket
            val socket = controlSocket
            if (socket != null && socket.isConnected && !socket.isClosed) {
                try {
                    val outputStream = socket.getOutputStream()
                    outputStream.write(buffer)
                    outputStream.flush()
                    LogManager.d(LogTags.SCRCPY_CLIENT, "✓ 文本发送成功: ${textBytes.size} 字节")
                    return@withContext Result.success(true)
                } catch (e: Exception) {
                    LogManager.w(LogTags.SCRCPY_CLIENT, "控制 socket 发送失败，回退到 ADB: ${e.message}")
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
                    controlSocket = null
                }
            }

            // 回退到 ADB shell
            val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                ?: return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

            val hexString = buffer.joinToString("") { "%02x".format(it) }
            val command = "echo -n '$hexString' | xxd -r -p | nc 127.0.0.1 $LOCAL_PORT"

            val result = connection.executeShell(command)
            if (result.isSuccess) {
                LogManager.d(LogTags.SCRCPY_CLIENT, "✓ 文本发送成功: ${textBytes.size} 字节")
                Result.success(true)
            } else {
                LogManager.e(LogTags.SCRCPY_CLIENT, "文本发送失败")
                Result.failure(Exception(BilingualTexts.ERROR_SEND_FAILED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "发送文本失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 设置远程设备剪贴板并自动粘贴（支持中文）
     */
    suspend fun setClipboardAndPaste(text: String): Result<Boolean> = withContext(Dispatchers.IO) {
        if (currentDeviceId == null) {
            return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_NOT_CONNECTED.get()))
        }

        LogManager.d(LogTags.SCRCPY_CLIENT, "通过剪贴板注入文本: '$text'")

        return@withContext try {
            val connection = adbConnectionManager.getConnection(currentDeviceId!!)
                ?: return@withContext Result.failure(Exception(BilingualTexts.ERROR_DEVICE_CONNECTION_LOST.get()))

            // 方案：使用 ADB 设置剪贴板 + 发送粘贴按键
            // 1. 设置剪贴板内容
            val base64Text = android.util.Base64.encodeToString(
                text.toByteArray(Charsets.UTF_8),
                android.util.Base64.NO_WRAP
            )
            val setClipboardCmd = "am broadcast -a clipper.set -e text \"$base64Text\" 2>/dev/null || " +
                    "service call clipboard 1 i32 0 s16 com.android.shell s16 \"$text\""

            val clipResult = connection.executeShell(setClipboardCmd)
            if (clipResult.isFailure) {
                LogManager.w(LogTags.SCRCPY_CLIENT, "设置剪贴板失败，尝试直接粘贴")
            }

            // 2. 发送粘贴按键事件 (Ctrl+V: keycode 279 或使用 KEYCODE_PASTE)
            delay(100) // 等待剪贴板设置完成

            // 发送 KEYCODE_PASTE (279)
            sendKeyEvent(279) // KEYCODE_PASTE

            LogManager.d(LogTags.SCRCPY_CLIENT, "✓ 文本注入成功")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "注入文本失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Scrcpy Audio Stream 包装类
     * 流程：[codec(4)] + N × (pts(8) + len(4) + data)
     * 协议格式（大端序）：
     * - codec ID: 4 bytes (big-endian)
     * - 每个包: 12 bytes header (PTS 8 bytes + size 4 bytes, big-endian) + payload
     * - PTS 最高位 (bit 63): config packet flag
     * - PTS 次高位 (bit 62): key frame flag
     */
    private class ScrcpyAudioStream(private val socket: Socket) : com.mobile.scrcpy.android.core.media.AudioStream {
        private val dataInputStream = java.io.DataInputStream(socket.inputStream)

        override val codec: String
        override val sampleRate: Int = 48000  // scrcpy 固定 48000
        override val channelCount: Int = 2    // scrcpy 固定 2

        init {
            socket.soTimeout = 10000  // 10 秒超时

            // 1️⃣ 读 AudioHeader (4 bytes, big-endian)
            val codecId = dataInputStream.readInt()  // uint32 codec (big-endian)

            codec = when (codecId) {
                0x6f707573 -> "opus"  // "opus" 的 ASCII
                0x00616163 -> "aac"   // "aac" 的 ASCII
                0x666c6163 -> "flac"  // "flac" 的 ASCII
                0x00726177 -> "raw"   // "raw" 的 ASCII
                else -> {
                    LogManager.w("ScrcpyAudioStream", "未知 codec ID: 0x${codecId.toString(16)}, 使用 opus")
                    "opus"
                }
            }

            LogManager.d("ScrcpyAudioStream", "音频配置: codec=$codec, rate=$sampleRate, channels=$channelCount")
        }

        private var packetCount = 0

        @Throws(IOException::class)
        override fun read(): AdbShellPacket {
            try {
                // 2️⃣ 循环读包：pts(8) + size(4) + payload (全部大端序)
                val ptsAndFlags = dataInputStream.readLong()   // uint64 pts (包含标志位, big-endian)
                val packetSize = dataInputStream.readInt() // uint32 size (big-endian)

                if (packetSize <= 0 || packetSize > 4 * 1024 * 1024) {
                    LogManager.e("AudioDecoder", "音频包大小异常: $packetSize, pts=$ptsAndFlags")
                    return AdbShellPacket.Exit(byteArrayOf(0))
                }

                // 3️⃣ 读 payload（裸编码帧）
                val packet = ByteArray(packetSize)
                dataInputStream.readFully(packet, 0, packetSize)

                packetCount++

                // 检查标志位
                val isConfig = (ptsAndFlags and PACKET_FLAG_CONFIG) != 0L
                val isKeyFrame = (ptsAndFlags and PACKET_FLAG_KEY_FRAME) != 0L
                val actualPts = ptsAndFlags and PACKET_PTS_MASK

                // 打印数据包信息（前10个包和每50个包打印一次）
                if (packetCount <= 10 || packetCount % 50 == 0) {
                    val flags = buildString {
                        if (isConfig) append("CONFIG ")
                        if (isKeyFrame) append("KEY ")
                        if (isEmpty()) append("NORMAL")
                    }

                    // 打印前16字节的十六进制数据
                    val previewSize = minOf(16, packet.size)
                    val hexPreview = packet.take(previewSize).joinToString(" ") { "%02X".format(it) }

                    LogManager.d("AudioDecoder", "音频包 #$packetCount: size=$packetSize, pts=$actualPts, flags=[$flags], data=$hexPreview...")

                    // 如果是小包，打印完整数据
                    if (packetSize <= 10) {
                        LogManager.w("AudioDecoder", "⚠️ 异常小包 #$packetCount: 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}")
                    }
                }

                if (isConfig) {
                    LogManager.d("AudioDecoder", "收到配置包 #$packetCount: size=$packetSize, 完整数据=${packet.joinToString(" ") { "%02X".format(it) }}")
                }

                return AdbShellPacket.StdOut(packet)
            } catch (_: java.net.SocketTimeoutException) {
                return AdbShellPacket.StdOut(byteArrayOf())
            } catch (_: java.io.EOFException) {
                LogManager.d("AudioDecoder", "音频流结束，共接收 $packetCount 个包")
                return AdbShellPacket.Exit(byteArrayOf(0))
            } catch (e: IOException) {
                LogManager.e("AudioDecoder", "音频流读取错误: ${e.message}", e)
                throw e
            }
        }

        override fun close() {
            try {
                socket.close()
            } catch (e: IOException) {
                LogManager.w("ScrcpyAudioStream", "关闭 Socket 失败: ${e.message}")
            }
        }
    }

    /**
     * Scrcpy Socket Stream 包装类
     * 按照 scrcpy 3.3.4 协议：12字节 frame header + 数据包内容
     * Frame header 格式：
     * - PTS (8 bytes, 其中最高2位是标志位)
     * - packet size (4 bytes)
     */
    private inner class ScrcpySocketStream(private val socket: Socket) : VideoStream {
        private val dataInputStream = java.io.DataInputStream(socket.inputStream)

        init {
            socket.soTimeout = 5000 // 5秒超时
        }

        @Throws(IOException::class)
        override fun read(): AdbShellPacket {
            try {
                // 检查数据是否可用
                if (dataInputStream.available() <= 0) {
                    // 没有数据，返回空包（避免阻塞）
                    return AdbShellPacket.StdOut(byteArrayOf())
                }

                // 读取 frame header（12字节）
                dataInputStream.readLong() // 8字节 PTS（包含标志位）
                val packetSize = dataInputStream.readInt() // 4字节包大小

                // 检查包大小是否合理（最大4MB）
                if (packetSize <= 0 || packetSize > 4 * 1024 * 1024) {
                    LogManager.e("ScrcpySocketStream", "数据包大小异常: $packetSize")
                    updateConnectionStateOnError("数据包大小异常")
                    return AdbShellPacket.Exit(byteArrayOf(0))
                }

                // 读取完整数据包
                val packet = ByteArray(packetSize)
                dataInputStream.readFully(packet, 0, packetSize)

                return AdbShellPacket.StdOut(packet)
            } catch (_: java.net.SocketTimeoutException) {
                // 读取超时，返回空数据继续等待
                return AdbShellPacket.StdOut(byteArrayOf())
            } catch (_: java.io.EOFException) {
                // 流结束
                updateConnectionStateOnError("视频流已关闭")
                return AdbShellPacket.Exit(byteArrayOf(0))
            } catch (e: IOException) {
                // 其他 IO 错误
                updateConnectionStateOnError("读取失败: ${e.message}")
                throw e
            }
        }

        override fun close() {
            try {
                socket.close()
            } catch (e: IOException) {
                LogManager.w("ScrcpySocketStream", "关闭 Socket 失败: ${e.message}")
            }
        }
    }

    /**
     * 当视频流出现错误时更新连接状态并触发重连
     */
    private fun updateConnectionStateOnError(message: String) {
        // 只在连接状态为 Connected 时更新，避免重复更新
        if (_connectionState.value is ConnectionState.Connected) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "${BilingualTexts.ERROR_CONNECTION_FAILED.get()}: $message")
            triggerReconnect()
        }
    }

    /**
     * 触发重连（带指数退避重试机制）
     */
    private fun triggerReconnect() {
        val deviceId = currentDeviceId
        if (deviceId == null) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "无法重连：设备 ID 为空")
            _connectionState.value = ConnectionState.Error("设备未连接")
            return
        }

        // 防止重复重连
        if (isReconnecting) {
            LogManager.w(LogTags.SCRCPY_CLIENT, "重连正在进行中，跳过本次重连请求")
            return
        }

        // 检查是否超过最大重试次数
        if (reconnectAttempts >= ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "重连失败：已达最大重试次数 ${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}")
            _connectionState.value = ConnectionState.Error("重连失败：已达最大重试次数")
            reconnectAttempts = 0
            isReconnecting = false
            return
        }

        reconnectAttempts++
        isReconnecting = true

        LogManager.d(LogTags.SCRCPY_CLIENT, "========== 触发重连 (尝试 $reconnectAttempts/${ScrcpyConstants.MAX_RECONNECT_ATTEMPTS}) ==========")
        LogManager.d(LogTags.SCRCPY_CLIENT, "设备 ID: $deviceId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 计算指数退避延迟时间：2^(n-1) * base_delay
                // 第1次: 2秒, 第2次: 4秒, 第3次: 8秒
                val delayMs = (1L shl (reconnectAttempts - 1)) * ScrcpyConstants.DEFAULT_RECONNECT_DELAY
                LogManager.d(LogTags.SCRCPY_CLIENT, "等待 ${delayMs}ms 后重连...")

                // 更新为 Reconnecting 状态
                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Reconnecting
                }

                delay(delayMs)

                // 检查 ADB 连接状态
                LogManager.d(LogTags.SCRCPY_CLIENT, "检查 ADB 连接状态...")
                val connection = adbConnectionManager.getConnection(deviceId)
                if (connection == null) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不存在")
                    handleReconnectFailure("ADB 会话已断开，请重新连接设备")
                    return@launch
                }

                // 测试 ADB 连接是否可用
                val testResult = connection.executeShell("echo test", retryOnFailure = false)
                if (testResult.isFailure) {
                    LogManager.e(LogTags.SCRCPY_CLIENT, "✗ ADB 连接不可用: ${testResult.exceptionOrNull()?.message}")
                    handleReconnectFailure("ADB 会话已断开，请重新连接设备")
                    return@launch
                }
                LogManager.d(LogTags.SCRCPY_CLIENT, "✓ ADB 连接正常")

                // 尝试重新连接
                LogManager.d(LogTags.SCRCPY_CLIENT, "尝试重新连接...")
                withContext(Dispatchers.Main) {
                    _connectionState.value = ConnectionState.Connecting
                }

                // 使用缓存的连接参数重新连接
                val reconnectResult = connectByDeviceId(
                    deviceId = deviceId,
                    maxSize = lastMaxSize,
                    bitRate = lastBitRate,
                    maxFps = lastMaxFps,
                    videoCodec = lastVideoCodec,
                    enableAudio = lastEnableAudio,
                    stayAwake = lastStayAwake,
                    turnScreenOff = lastTurnScreenOff,
                    powerOffOnClose = lastPowerOffOnClose
                )

                if (reconnectResult.isSuccess) {
                    LogManager.d(LogTags.SCRCPY_CLIENT, "========== 重连成功 (尝试 $reconnectAttempts 次) ==========")
                    withContext(Dispatchers.Main) {
                        _connectionState.value = ConnectionState.Connected
                    }
                    // 重置重连状态（在 connectByDeviceId 成功后会自动重置）
                    isReconnecting = false
                } else {
                    val errorMsg = reconnectResult.exceptionOrNull()?.message ?: "未知错误"
                    LogManager.e(LogTags.SCRCPY_CLIENT, "========== 重连失败 (尝试 $reconnectAttempts 次) ==========")
                    LogManager.e(LogTags.SCRCPY_CLIENT, "错误: $errorMsg")

                    // 判断是否是永久性错误（不应重试）
                    if (isPermanentError(errorMsg)) {
                        LogManager.e(LogTags.SCRCPY_CLIENT, "检测到永久性错误，停止重试")
                        handleReconnectFailure("重连失败: $errorMsg")
                    } else if (reconnectAttempts < ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
                        // 还有重试机会，继续重试
                        LogManager.d(LogTags.SCRCPY_CLIENT, "将在延迟后再次尝试重连...")
                        isReconnecting = false
                        triggerReconnect()  // 递归调用进行下一次重试
                    } else {
                        // 达到最大重试次数
                        handleReconnectFailure("重连失败: $errorMsg")
                    }
                }

                LogManager.d(LogTags.SCRCPY_CLIENT, "========== 重连流程结束 ==========")
            } catch (e: Exception) {
                LogManager.e(LogTags.SCRCPY_CLIENT, "========== 重连过程出错 ==========")
                LogManager.e(LogTags.SCRCPY_CLIENT, "错误: ${e.message}", e)

                if (reconnectAttempts < ScrcpyConstants.MAX_RECONNECT_ATTEMPTS) {
                    // 还有重试机会
                    isReconnecting = false
                    triggerReconnect()
                } else {
                    handleReconnectFailure("重连失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 处理重连失败
     */
    private suspend fun handleReconnectFailure(errorMessage: String) {
        withContext(Dispatchers.Main) {
            _connectionState.value = ConnectionState.Error(errorMessage)
        }
        reconnectAttempts = 0
        isReconnecting = false
    }

    /**
     * 判断是否是永久性错误（不应重试的错误）
     */
    private fun isPermanentError(errorMessage: String): Boolean {
        // 永久性错误关键词列表
        val permanentErrorKeywords = listOf(
            "设备未连接",
            "设备连接已断开",
            "ADB 会话已断开",
            "未授权",
            "权限被拒绝",
            "不支持",
            "无效的参数"
        )

        return permanentErrorKeywords.any { errorMessage.contains(it, ignoreCase = true) }
    }

    /**
     * 启动前台服务（首次连接或添加设备）
     */
    private fun startForegroundService(deviceName: String, width: Int, height: Int) {
        try {
            val deviceId = currentDeviceId ?: return

            val intent = Intent(context, ScrcpyForegroundService::class.java).apply {
                action = ScrcpyForegroundService.ACTION_ADD_DEVICE
                putExtra(ScrcpyForegroundService.EXTRA_DEVICE_ID, deviceId)
                putExtra(ScrcpyForegroundService.EXTRA_DEVICE_NAME, deviceName)
            }

            ApiCompatHelper.startForegroundServiceCompat(context, intent)

            LogManager.d(LogTags.SCRCPY_CLIENT, "已添加设备到保活列表: $deviceName")
        } catch (e: Exception) {
            LogManager.e(LogTags.SCRCPY_CLIENT, "添加设备到保活列表失败: ${e.message}", e)
        }
    }

    /**
     * 获取当前连接的设备 ID
     */
    fun getCurrentDeviceId(): String? = currentDeviceId
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Connected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Reconnecting : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

/**
 * 触摸动作（对应 Android MotionEvent）
 */
object TouchAction {
    const val ACTION_DOWN = 0           // 第一个手指按下
    const val ACTION_UP = 1             // 最后一个手指抬起
    const val ACTION_MOVE = 2           // 手指移动
    const val ACTION_CANCEL = 3         // 取消
    const val ACTION_POINTER_DOWN = 5   // 额外手指按下（多指触摸）
    const val ACTION_POINTER_UP = 6     // 额外手指抬起（多指触摸）
}
