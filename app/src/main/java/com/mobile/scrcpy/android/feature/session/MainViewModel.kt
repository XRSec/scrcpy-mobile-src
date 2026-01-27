package com.mobile.scrcpy.android.feature.session

import com.mobile.scrcpy.android.common.LogManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobile.scrcpy.android.app.ScreenRemoteApp
import com.mobile.scrcpy.android.common.AppConstants
import com.mobile.scrcpy.android.common.NetworkConstants
import com.mobile.scrcpy.android.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.mobile.scrcpy.android.common.LogTags
import com.mobile.scrcpy.android.core.data.model.AppSettings
import com.mobile.scrcpy.android.core.data.model.ConnectionProgress
import com.mobile.scrcpy.android.core.data.model.DefaultGroups
import com.mobile.scrcpy.android.core.data.model.DeviceGroup
import com.mobile.scrcpy.android.core.data.model.ScrcpyAction
import com.mobile.scrcpy.android.core.data.model.ScrcpySession
import com.mobile.scrcpy.android.core.data.model.parseMaxSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

sealed class ConnectStatus {
    object Idle : ConnectStatus()
    data class Connecting(val sessionId: String, val message: String) : ConnectStatus()
    data class Connected(val sessionId: String) : ConnectStatus()
    data class Failed(val sessionId: String, val error: String) : ConnectStatus()
    data class Unauthorized(val sessionId: String) : ConnectStatus()
}

class MainViewModel : ViewModel() {
    private val preferencesManager = PreferencesManager(ScreenRemoteApp.instance)
    private val sessionRepository = SessionRepository(ScreenRemoteApp.instance)
    private val groupRepository = GroupRepository(ScreenRemoteApp.instance)
    private val adbConnectionManager = ScreenRemoteApp.instance.adbConnectionManager
    private val scrcpyClient = com.mobile.scrcpy.android.feature.scrcpy.ScrcpyClient(
        ScreenRemoteApp.instance,
        adbConnectionManager
    )

    // 当前连接任务的 Job，用于取消正在进行的连接
    private var connectJob: kotlinx.coroutines.Job? = null

    val sessions: StateFlow<List<ScrcpySession>> = sessionRepository.sessionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val sessionDataList: StateFlow<List<SessionData>> = sessionRepository.sessionDataFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // 分组列表（从 GroupRepository 加载）
    val groups: StateFlow<List<DeviceGroup>> = groupRepository.groupsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // 当前选中的分组路径（用于首页筛选）
    private val _selectedGroupPath = MutableStateFlow<String>(DefaultGroups.ALL_DEVICES)
    val selectedGroupPath: StateFlow<String> = _selectedGroupPath.asStateFlow()
    
    // 自动化页面的分组路径（独立管理）
    private val _selectedAutomationGroupPath = MutableStateFlow<String>(DefaultGroups.ALL_DEVICES)
    val selectedAutomationGroupPath: StateFlow<String> = _selectedAutomationGroupPath.asStateFlow()

    // 根据选中分组路径筛选的会话列表
    val filteredSessions: StateFlow<List<SessionData>> = combine(
        sessionDataList,
        _selectedGroupPath,
        groups
    ) { sessions, groupPath, groupsList ->
        when (groupPath) {
            DefaultGroups.ALL_DEVICES -> sessions
            DefaultGroups.UNGROUPED -> sessions.filter { it.groupIds.isEmpty() }
            else -> sessions.filter { session ->
                // 检查 groupIds 是否包含当前分组或其子分组
                val currentGroup = groupsList.find { it.path == groupPath }
                if (currentGroup != null) {
                    session.groupIds.any { groupId ->
                        val group = groupsList.find { it.id == groupId }
                        group != null && (group.path == groupPath || group.path.startsWith("$groupPath/"))
                    }
                } else {
                    false
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
        initialValue = emptyList()
    )

    private val _actions = MutableStateFlow<List<ScrcpyAction>>(emptyList())
    val actions: StateFlow<List<ScrcpyAction>> = _actions.asStateFlow()

    private val _showAddSessionDialog = MutableStateFlow(false)
    val showAddSessionDialog: StateFlow<Boolean> = _showAddSessionDialog.asStateFlow()

    private val _editingSessionId = MutableStateFlow<String?>(null)
    val editingSessionId: StateFlow<String?> = _editingSessionId.asStateFlow()

    private val _showAddActionDialog = MutableStateFlow(false)
    val showAddActionDialog: StateFlow<Boolean> = _showAddActionDialog.asStateFlow()

    private val _connectStatus = MutableStateFlow<ConnectStatus>(ConnectStatus.Idle)
    val connectStatus: StateFlow<ConnectStatus> = _connectStatus.asStateFlow()

    private val _connectedSessionId = MutableStateFlow<String?>(null)
    val connectedSessionId: StateFlow<String?> = _connectedSessionId.asStateFlow()

    // 连接进度状态
    val connectionProgress: StateFlow<List<ConnectionProgress>> = scrcpyClient.connectionProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val settings: StateFlow<AppSettings> = preferencesManager.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(AppConstants.STATEFLOW_SUBSCRIBE_TIMEOUT_MS),
            initialValue = AppSettings()
        )

    // ============ 分组管理方法 ============

    /**
     * 选择分组路径（用于首页筛选）
     */
    fun selectGroup(groupPath: String) {
        _selectedGroupPath.value = groupPath
    }
    
    /**
     * 选择自动化分组路径
     */
    fun selectAutomationGroup(groupPath: String) {
        _selectedAutomationGroupPath.value = groupPath
    }

    /**
     * 添加分组
     */
    fun addGroup(name: String, parentPath: String, description: String = "", type: com.mobile.scrcpy.android.core.data.model.GroupType = com.mobile.scrcpy.android.core.data.model.GroupType.SESSION) {
        viewModelScope.launch {
            val path = if (parentPath == "/") "/$name" else "$parentPath/$name"
            val groupData = com.mobile.scrcpy.android.feature.session.GroupData(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                type = type.name,
                path = path,
                parentPath = parentPath,
                description = description
            )
            groupRepository.addGroup(groupData)
        }
    }

    /**
     * 更新分组
     */
    fun updateGroup(group: DeviceGroup) {
        viewModelScope.launch {
            val groupData = com.mobile.scrcpy.android.feature.session.GroupData(
                id = group.id,
                name = group.name,
                type = group.type.name,
                path = group.path,
                parentPath = group.parentPath,
                description = group.description,
                createdAt = group.createdAt
            )
            groupRepository.updateGroup(groupData)
        }
    }

    /**
     * 删除分组
     */
    fun removeGroup(groupId: String) {
        viewModelScope.launch {
            groupRepository.removeGroup(groupId)
        }
    }


    /**
     * 统计每个分组下的会话数量
     */
    fun getSessionCountByGroup(): Map<String, Int> {
        val countMap = mutableMapOf<String, Int>()
        sessionDataList.value.forEach { session ->
            session.groupIds.forEach { groupId ->
                countMap[groupId] = (countMap[groupId] ?: 0) + 1
            }
        }
        return countMap
    }



    fun showAddSessionDialog() {
        _editingSessionId.value = null
        _showAddSessionDialog.value = true
    }

    fun showEditSessionDialog(sessionId: String) {
        _editingSessionId.value = sessionId
        _showAddSessionDialog.value = true
    }

    fun hideAddSessionDialog() {
        _showAddSessionDialog.value = false
        _editingSessionId.value = null
    }

    fun showAddActionDialog() {
        _showAddActionDialog.value = true
    }

    fun hideAddActionDialog() {
        _showAddActionDialog.value = false
    }

    fun saveSessionData(sessionData: SessionData) {
        if (_editingSessionId.value != null) {
            updateSessionData(sessionData)
        } else {
            addSessionData(sessionData)
        }
    }

    private fun addSessionData(sessionData: SessionData) {
        viewModelScope.launch {
            sessionRepository.addSession(sessionData)
            hideAddSessionDialog()
        }
    }

    private fun updateSessionData(sessionData: SessionData) {
        viewModelScope.launch {
            sessionRepository.updateSession(sessionData)
            hideAddSessionDialog()
        }
    }

    /**
     * 更新会话的编解码器缓存
     * @param sessionId 会话 ID
     * @param videoDecoder 视频解码器名称（null 表示不更新）
     * @param audioDecoder 音频解码器名称（null 表示不更新）
     */
    suspend fun updateCodecCache(
        sessionId: String,
        videoDecoder: String? = null,
        audioDecoder: String? = null
    ) {
        val currentData = sessionRepository.getSessionData(sessionId) ?: return

        val updatedData = currentData.copy(
            cachedVideoDecoder = videoDecoder ?: currentData.cachedVideoDecoder,
            cachedAudioDecoder = audioDecoder ?: currentData.cachedAudioDecoder,
            codecCacheTimestamp = System.currentTimeMillis()
        )

        sessionRepository.updateSession(updatedData)
    }

    fun removeSession(id: String) {
        viewModelScope.launch {
            sessionRepository.removeSession(id)
        }
    }

    fun addAction(action: ScrcpyAction) {
        _actions.value = _actions.value + action
        hideAddActionDialog()
    }

    fun removeAction(id: String) {
        _actions.value = _actions.value.filter { it.id != id }
    }

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            preferencesManager.updateSettings(settings)
        }
    }

    fun connectToDevice(host: String, port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = scrcpyClient.connect(host, port)
                if (result.isFailure) {
                    LogManager.e(LogTags.MAIN_VIEW_MODEL, "连接失败: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "连接异常: ${e.message}", e)
            }
        }
    }

    fun connectSession(sessionId: String) {
        // 取消之前的连接任务
        connectJob?.cancel()

        connectJob = viewModelScope.launch(Dispatchers.IO) {
            val sessionData = sessionRepository.getSessionData(sessionId)
            if (sessionData == null) {
                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Failed(sessionId, "会话不存在")
                    _connectedSessionId.value = null
                }
                return@launch
            }

            // 判断是否为重连（已经有 connectedSessionId）
            val isReconnecting = _connectedSessionId.value != null

            // 立即设置 connectedSessionId，让 RemoteDisplayScreen 显示（即使连接失败也能看到进度）
            withContext(Dispatchers.Main) {
                _connectedSessionId.value = sessionId
                _connectStatus.value = ConnectStatus.Connecting(
                    sessionId,
                    if (isReconnecting) "Reconnecting..." else "Connecting to ADB..."
                )
            }

            try {
                val port = sessionData.port.toIntOrNull() ?: NetworkConstants.DEFAULT_ADB_PORT_INT
                // 使用扩展函数解析 maxSize：空字符串使用默认值，"0" 表示不限制
                val maxSize = sessionData.maxSize.parseMaxSize()
                val bitrate = sessionData.bitrate.toIntOrNull() ?: ScrcpyConstants.DEFAULT_BITRATE_INT
                val maxFps = sessionData.maxFps.toIntOrNull() ?: ScrcpyConstants.DEFAULT_MAX_FPS

                val result = scrcpyClient.connect(
                    host = sessionData.host,
                    port = port,
                    maxSize = maxSize,
                    bitRate = bitrate,
                    maxFps = maxFps,
                    videoCodec = sessionData.videoCodec,
                    videoEncoder = sessionData.videoEncoder,
                    enableAudio = sessionData.enableAudio,
                    audioCodec = sessionData.audioCodec,
                    audioEncoder = sessionData.audioEncoder,
                    stayAwake = sessionData.stayAwake,
                    turnScreenOff = sessionData.turnScreenOff,
                    powerOffOnClose = sessionData.powerOffOnClose
                )

                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        _connectStatus.value = ConnectStatus.Connected(sessionId)
                    } else {
                        _connectStatus.value = ConnectStatus.Failed(
                            sessionId,
                            result.exceptionOrNull()?.message ?: "连接失败"
                        )
                        // 不清除 connectedSessionId，让用户能看到错误信息
                    }
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "连接会话异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Failed(sessionId, e.message ?: "连接失败")
                    // 不清除 connectedSessionId，让用户能看到错误信息
                }
            }
        }
    }

    fun cancelConnect() {
        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                scrcpyClient.disconnect()
                withContext(Dispatchers.Main) {
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "取消连接异常: ${e.message}", e)
            }
        }
    }

    fun clearConnectStatus() {
        _connectStatus.value = ConnectStatus.Idle
        _connectedSessionId.value = null
    }

    fun disconnectFromDevice() {
        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "用户主动结束会话...")

                // 1. 断开 scrcpy 连接
                scrcpyClient.disconnect()

                // 2. 保留 ADB 保活（不移除前台服务保护）
                // ADB 连接会继续保活，除非断开异常或退出软件
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "✓ scrcpy 已断开，ADB 连接保持保活")

                withContext(Dispatchers.Main) {
                    // 更新连接状态，使界面切换回主界面
                    _connectStatus.value = ConnectStatus.Idle
                    _connectedSessionId.value = null
                }
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "结束会话完成")
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "结束会话异常: ${e.message}", e)
            }
        }
    }

    /**
     * 处理连接丢失（Socket closed / Stream closed）
     * 只断开 scrcpy 连接，保留 ADB 连接和前台服务保活
     * 不清除 connectedSessionId，让用户停留在 RemoteDisplayScreen 看到重连进度
     */
    fun handleConnectionLost() {
        LogManager.w(LogTags.MAIN_VIEW_MODEL, "⚠️ 处理连接丢失：断开 scrcpy，保留 ADB 保活")

        // 取消正在进行的连接任务
        connectJob?.cancel()
        connectJob = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 断开 scrcpy 连接（保留 ADB 连接）
                scrcpyClient.disconnect()

                // 2. 不移除前台服务保护，让 ADB 连接继续保活
                // 前台服务会通过心跳检测自动维护 ADB 连接
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "✓ scrcpy 已断开，ADB 连接保持保活")

                // 3. 更新 UI 状态（保持 connectedSessionId，让用户停留在 RemoteDisplayScreen）
                withContext(Dispatchers.Main) {
                    // 设置为 Connecting 状态，触发重连 UI
                    val sessionId = _connectedSessionId.value
                    if (sessionId != null) {
                        _connectStatus.value = ConnectStatus.Connecting(sessionId, "Connection lost, preparing to reconnect...")
                    } else {
                        _connectStatus.value = ConnectStatus.Idle
                    }
                    // 不清除 connectedSessionId，让用户停留在 RemoteDisplayScreen
                }

                LogManager.d(LogTags.MAIN_VIEW_MODEL, "✓ 连接丢失处理完成")
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "处理连接丢失异常: ${e.message}", e)
            }
        }
    }

    // 临时断开连接（后台时使用），不改变连接状态
    fun pauseConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "暂停连接...")
                scrcpyClient.disconnect()
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "暂停连接异常: ${e.message}", e)
            }
        }
    }

    fun getConnectionState() = scrcpyClient.connectionState
    fun getVideoStream() = scrcpyClient.videoStreamState
    fun getAudioStream() = scrcpyClient.audioStreamState
    fun getVideoResolution() = scrcpyClient.videoResolution

    suspend fun sendKeyEvent(keyCode: Int): Result<Boolean> {
        return scrcpyClient.sendKeyEvent(keyCode)
    }

    suspend fun sendKeyEvent(keyCode: Int, action: Int, metaState: Int): Result<Boolean> {
        return scrcpyClient.sendKeyEvent(keyCode, action, 0, metaState)
    }

    suspend fun sendText(text: String): Result<Boolean> {
        return scrcpyClient.sendText(text)
    }

    suspend fun sendTouchEvent(
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = 1.0f
    ): Result<Boolean> {
        return scrcpyClient.sendTouchEvent(action, pointerId, x, y, screenWidth, screenHeight, pressure)
    }

    /**
     * 发送滑动手势
     * @param startX 起始 X 坐标
     * @param startY 起始 Y 坐标
     * @param endX 结束 X 坐标
     * @param endY 结束 Y 坐标
     * @param duration 滑动持续时间（毫秒）
     */
    suspend fun sendSwipeGesture(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 300
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val resolution = scrcpyClient.videoResolution.value
                ?: return@withContext Result.failure(Exception("无法获取视频分辨率"))
            val (screenWidth, screenHeight) = resolution

            // 计算滑动步数（每 16ms 一帧，约 60fps）
            val steps = (duration / 16).toInt().coerceAtLeast(10)
            val pointerId = 0L

            // 发送按下事件
            sendTouchEvent(0, pointerId, startX, startY, screenWidth, screenHeight)
            kotlinx.coroutines.delay(16)

            // 发送移动事件
            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                val currentX = (startX + (endX - startX) * progress).toInt()
                val currentY = (startY + (endY - startY) * progress).toInt()
                sendTouchEvent(2, pointerId, currentX, currentY, screenWidth, screenHeight)
                kotlinx.coroutines.delay(16)
            }

            // 发送抬起事件
            sendTouchEvent(1, pointerId, endX, endY, screenWidth, screenHeight)

            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.CONTROL_HANDLER, "发送滑动手势失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 唤醒远程设备屏幕
     */
    suspend fun wakeUpScreen(): Result<Boolean> {
        return scrcpyClient.wakeUpScreen()
    }

    /**
     * 执行 Shell 命令
     * @param command Shell 命令
     * @return 命令执行结果
     */
    suspend fun executeShellCommand(command: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 获取当前设备 ID
                val deviceId = scrcpyClient.getCurrentDeviceId()
                    ?: return@withContext Result.failure(Exception("未连接设备"))

                // 获取 ADB 连接
                val connection = adbConnectionManager.getConnection(deviceId)
                    ?: return@withContext Result.failure(Exception("Device connection lost"))

                // 执行 Shell 命令
                connection.executeShell(command)
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "执行 Shell 命令失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun generateAdbKeys(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val keysDir = File(ScreenRemoteApp.instance.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 删除旧密钥
                if (privateKeyFile.exists()) {
                    privateKeyFile.delete()
                }
                if (publicKeyFile.exists()) {
                    publicKeyFile.delete()
                }

                // 生成新密钥
                dadb.AdbKeyPair.generate(privateKeyFile, publicKeyFile)

                LogManager.d(LogTags.MAIN_VIEW_MODEL, "新的 ADB 密钥对生成成功")
                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "生成 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    fun getAdbPublicKey(): Flow<String?> = flow {
        emit(adbConnectionManager.getPublicKey())
    }

    fun getAdbKeysInfo(): Flow<AdbKeysInfo> = flow {
        val keysDir = File(ScreenRemoteApp.instance.filesDir, "adb_keys").absolutePath
        val keysDirFile = File(keysDir)

        val privateKeyFile = File(keysDirFile, "adbkey")
        val publicKeyFile = File(keysDirFile, "adbkey.pub")

        val privateKey = if (privateKeyFile.exists()) {
            privateKeyFile.readText()
        } else {
            ""
        }

        val publicKey = if (publicKeyFile.exists()) {
            publicKeyFile.readText()
        } else {
            ""
        }

        emit(AdbKeysInfo(keysDir, privateKey, publicKey))
    }

    suspend fun saveAdbKeys(privateKey: String, publicKey: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val keysDir = File(ScreenRemoteApp.instance.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 保存私钥
                privateKeyFile.writeText(privateKey)
                // 保存公钥
                publicKeyFile.writeText(publicKey)

                LogManager.d(LogTags.MAIN_VIEW_MODEL, "ADB 密钥保存成功")
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "私钥文件: ${privateKeyFile.absolutePath}")
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "公钥文件: ${publicKeyFile.absolutePath}")

                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "保存 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    suspend fun exportAdbKeys(): Result<String> {
        return try {
            val keysDir = File(ScreenRemoteApp.instance.filesDir, "adb_keys").absolutePath
            Result.success(keysDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importAdbKeys(privateKey: String, publicKey: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val keysDir = File(ScreenRemoteApp.instance.filesDir, "adb_keys")
                if (!keysDir.exists()) {
                    keysDir.mkdirs()
                }

                val privateKeyFile = File(keysDir, "adbkey")
                val publicKeyFile = File(keysDir, "adbkey.pub")

                // 导入私钥
                privateKeyFile.writeText(privateKey)
                // 导入公钥
                publicKeyFile.writeText(publicKey)

                LogManager.d(LogTags.MAIN_VIEW_MODEL, "ADB 密钥导入成功")
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "私钥文件: ${privateKeyFile.absolutePath}")
                LogManager.d(LogTags.MAIN_VIEW_MODEL, "公钥文件: ${publicKeyFile.absolutePath}")

                Result.success(Unit)
            } catch (e: Exception) {
                LogManager.e(LogTags.MAIN_VIEW_MODEL, "导入 ADB 密钥失败: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}

data class AdbKeysInfo(
    val keysDir: String,
    val privateKey: String,
    val publicKey: String
)
