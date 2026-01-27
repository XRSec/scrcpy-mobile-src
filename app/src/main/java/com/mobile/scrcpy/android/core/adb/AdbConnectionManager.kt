package com.mobile.scrcpy.android.core.adb

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbDevice
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.AppConstants
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import com.mobile.scrcpy.android.core.adb.usb.UsbAdbManager
import com.mobile.scrcpy.android.core.adb.usb.UsbDadb
import com.mobile.scrcpy.android.core.data.model.ConnectionType
import dadb.Dadb
import dadb.AdbKeyPair
import dadb.adbserver.AdbServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 全局 ADB 连接管理器
 * 负责管理所有设备的 ADB 连接（TCP 和 USB），保持会话不主动关闭
 * 
 * USB 连接支持：
 * - 使用 dadb 库的 AdbServer 功能连接 USB 设备
 * - 参考 Easycontrol 的 USB 设备发现逻辑
 */
class AdbConnectionManager private constructor(private val context: Context) {
    // 设备连接池：deviceId -> AdbConnection
    private val connectionPool = ConcurrentHashMap<String, AdbConnection>()
    
    // 连接状态流
    private val _connectedDevices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    val connectedDevices: StateFlow<List<DeviceInfo>> = _connectedDevices.asStateFlow()
    
    // ADB 密钥对（全局共享）
    private var keyPair: AdbKeyPair? = null
    
    // USB 管理器
    private val usbAdbManager: UsbAdbManager by lazy { UsbAdbManager(context) }
    
    // 心跳保活任务
    private val keepAliveScope = CoroutineScope(Dispatchers.IO)
    private var keepAliveJob: Job? = null
    private val keepAliveInterval = 30_000L // 30秒心跳
    
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AdbConnectionManager? = null
        
        fun getInstance(context: Context): AdbConnectionManager {
            return instance ?: synchronized(this) {
                instance ?: AdbConnectionManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_MANAGER_INIT.get())
        initKeyPair()
        startKeepAlive()
    }
    
    /**
     * 启动连接保活任务
     */
    private fun startKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = keepAliveScope.launch {
            while (isActive) {
                delay(keepAliveInterval)
                
                // 对所有连接执行心跳检测
                val failedConnections = mutableListOf<String>()
                
                connectionPool.values.forEach { connection ->
                    val result = connection.executeShell("echo 1", retryOnFailure = false)
                    if (result.isFailure) {
                        val error = result.exceptionOrNull()
                        LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_HEARTBEAT_FAILED.get()}: ${connection.deviceId} - ${error?.message}")
                        
                        // 如果是 ECONNREFUSED，说明 ADB 已断开，标记为失效
                        if (error?.message?.contains(BilingualTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()) == true) {
                            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CONNECTION_DETECTED_DISCONNECTED.get()}: ${connection.deviceId}")
                            failedConnections.add(connection.deviceId)
                        }
                    }
                }
                
                // 清理失效的连接
                failedConnections.forEach { deviceId ->
                    LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CLEANUP_INVALID_CONNECTION.get()}: $deviceId")
                    connectionPool.remove(deviceId)?.close()
                    updateConnectedDevices()
                }
            }
        }
        LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_KEEPALIVE_STARTED.get()}（${BilingualTexts.LABEL_INTERVAL.get()}: ${keepAliveInterval}ms）")
    }
    
    /**
     * 初始化 ADB 密钥对
     */
    private fun initKeyPair() {
        try {
            val keysDir = File(context.filesDir, "adb_keys")
            if (!keysDir.exists()) {
                keysDir.mkdirs()
            }
            
            val privateKeyFile = File(keysDir, "adbkey")
            val publicKeyFile = File(keysDir, "adbkey.pub")
            
            if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
                LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_GENERATE_NEW_KEYPAIR.get())
                AdbKeyPair.generate(privateKeyFile, publicKeyFile)
            }
            
            keyPair = AdbKeyPair.read(privateKeyFile, publicKeyFile)
            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_KEYPAIR_LOADED.get())
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_KEYPAIR_INIT_FAILED.get()}: ${e.message}", e)
        }
    }
    
    /**
     * 连接设备
     * @param host 设备 IP 地址
     * @param port ADB 端口，默认 5555
     * @param deviceName 设备名称（可选，用于显示）
     * @return 设备 ID（host:port）
     */
    suspend fun connectDevice(
        host: String,
        port: Int = 5555,
        deviceName: String? = null,
        forceReconnect: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val deviceId = "$host:$port"
            
            if (keyPair == null) {
                return@withContext Result.failure(Exception(BilingualTexts.ADB_KEYPAIR_NOT_INITIALIZED.get()))
            }
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.ADB_START_CONNECTING.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_TARGET_ADDRESS.get()}: $deviceId")
            
            // 检查已有连接
            connectionPool[deviceId]?.let { existingConnection ->
                if (forceReconnect) {
                    // 强制重连：清理旧连接
                    LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_FORCE_RECONNECT_CLEANUP.get())
                    runCatching { existingConnection.close() }
                    connectionPool.remove(deviceId)
                } else {
                    // 验证已有连接
                    LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_VERIFYING_CONNECTION.get())
                    val isValid = verifyConnectionInternal(existingConnection)
                    if (isValid) {
                        LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_CONNECTION_VERIFIED.get()}")
                        return@withContext Result.success(deviceId)
                    } else {
                        LogManager.w(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CONNECTION_VERIFY_FAILED.get())
                        runCatching { existingConnection.close() }
                        connectionPool.remove(deviceId)
                    }
                }
            }
            
            // 创建新的 ADB 连接
            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CREATING_NEW_CONNECTION.get())
            val dadb = try {
                Dadb.create(host, port, keyPair!!)
            } catch (e: java.net.ConnectException) {
                LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CONNECTION_REFUSED.get()}: ${e.message}")
                return@withContext Result.failure(Exception(BilingualTexts.ADB_CONNECTION_REFUSED_DETAILS.get()))
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_DADB_CREATED.get()}")
            
            // 验证新连接
            val verifyResult = verifyDadb(dadb)
            if (verifyResult.isFailure) {
                return@withContext verifyResult
            }
            
            // 创建连接对象（使用临时设备信息，后台异步获取完整信息）
            val tempDeviceInfo = DeviceInfo(
                deviceId = deviceId,
                name = deviceName ?: deviceId,
                model = "Unknown",
                manufacturer = "Unknown",
                androidVersion = "Unknown",
                serialNumber = "",
                connectionType = ConnectionType.TCP
            )
            
            val connection = AdbConnection(
                deviceId = deviceId, host = host,
                port = port, dadb = dadb,
                deviceInfo = tempDeviceInfo
            )
            
            // 加入连接池
            connectionPool[deviceId] = connection
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_ADDED_TO_POOL.get()}")
            
            // 后台异步获取完整设备信息（不阻塞连接流程）
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullDeviceInfo = getDeviceInfo(dadb, deviceId, deviceName, ConnectionType.TCP)
                    connection.deviceInfo = fullDeviceInfo
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.LABEL_DEVICE_INFO.get()}: ${fullDeviceInfo.name} (${fullDeviceInfo.model})")
                } catch (e: Exception) {
                    LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_GET_DEVICE_INFO_FAILED.get()}: ${e.message}")
                }
            }
            
            // 更新连接设备列表
            updateConnectedDevices()
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.ADB_CONNECTION_SUCCESS.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_DEVICE_ID.get()}: $deviceId")
            Result.success(deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.ADB_CONNECTION_FAILED_TITLE.get()} ==========")
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ERROR_LABEL.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 连接 USB 设备
     * @param usbDevice USB 设备对象
     * @param deviceName 设备名称（可选）
     * @return 设备 ID（usb:serialNumber）
     */
    suspend fun connectUsbDevice(
        usbDevice: UsbDevice,
        deviceName: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val serialNumber = ApiCompatHelper.getUsbDeviceSerialNumber(usbDevice) ?: usbDevice.deviceName
            val deviceId = "usb:$serialNumber"
            
            if (keyPair == null) {
                return@withContext Result.failure(Exception(BilingualTexts.ADB_KEYPAIR_NOT_INITIALIZED.get()))
            }
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.USB_CONNECTING_DEVICE.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.USB_SERIAL_NUMBER.get()}: $serialNumber")
            
            // 检查已有连接
            connectionPool[deviceId]?.let { existingConnection ->
                LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_VERIFYING_CONNECTION.get())
                val isValid = verifyConnectionInternal(existingConnection)
                if (isValid) {
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_CONNECTION_VERIFIED.get()}")
                    return@withContext Result.success(deviceId)
                } else {
                    LogManager.w(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CONNECTION_VERIFY_FAILED.get())
                    runCatching { existingConnection.close() }
                    connectionPool.remove(deviceId)
                }
            }
            
            // 请求 USB 权限
            val permissionResult = usbAdbManager.requestUsbPermission(usbDevice)
            if (permissionResult.isFailure) {
                return@withContext permissionResult.map { deviceId }
            }
            
            // 使用 USB ADB 通道直接连接
            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CREATING_NEW_CONNECTION.get())
            val dadb = try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
                UsbDadb(usbManager, usbDevice, keyPair!!, deviceId)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.USB_CONNECT_FAILED.get()}: ${e.message}")
                return@withContext Result.failure(Exception("${BilingualTexts.USB_CONNECT_FAILED.get()}: ${e.message}"))
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_DADB_CREATED.get()}")
            
            // 验证连接
            val verifyResult = verifyDadb(dadb)
            if (verifyResult.isFailure) {
                return@withContext verifyResult
            }
            
            // 创建连接对象
            val tempDeviceInfo = DeviceInfo(
                deviceId = deviceId,
                name = deviceName ?: (usbDevice.productName ?: serialNumber),
                model = "Unknown",
                manufacturer = usbDevice.manufacturerName ?: "Unknown",
                androidVersion = "Unknown",
                serialNumber = serialNumber,
                connectionType = ConnectionType.USB
            )
            
            val connection = AdbConnection(
                deviceId = deviceId,
                host = "usb",
                port = 0,
                dadb = dadb,
                deviceInfo = tempDeviceInfo
            )
            
            // 加入连接池
            connectionPool[deviceId] = connection
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_ADDED_TO_POOL.get()}")
            
            // 后台异步获取完整设备信息
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val fullDeviceInfo = getDeviceInfo(dadb, deviceId, deviceName, ConnectionType.USB)
                    connection.deviceInfo = fullDeviceInfo
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.LABEL_DEVICE_INFO.get()}: ${fullDeviceInfo.name} (${fullDeviceInfo.model})")
                } catch (e: Exception) {
                    LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_GET_DEVICE_INFO_FAILED.get()}: ${e.message}")
                }
            }
            
            // 更新连接设备列表
            updateConnectedDevices()
            
            LogManager.d(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.ADB_CONNECTION_SUCCESS.get()} ==========")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_DEVICE_ID.get()}: $deviceId")
            Result.success(deviceId)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "========== ${BilingualTexts.USB_CONNECT_FAILED.get()} ==========")
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ERROR_LABEL.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 扫描 USB 设备
     * @return USB 设备列表
     */
    suspend fun scanUsbDevices() = usbAdbManager.scanUsbDevices()
    
    /**
     * 请求 USB 权限
     * @param device USB 设备对象
     * @return Result<Boolean> 权限请求结果
     */
    suspend fun requestUsbPermission(device: UsbDevice) = usbAdbManager.requestUsbPermission(device)
    
    /**
     * 获取 USB 设备列表
     */
    fun getUsbDevices() = usbAdbManager.usbDevices
    
    /**
     * 验证 ADB 连接是否可用（公共方法）
     * @param deviceId 设备 ID
     * @return true 表示连接可用，false 表示连接不可用
     */
    suspend fun verifyConnection(deviceId: String): Boolean {
        val connection = getConnection(deviceId) ?: return false
        return verifyConnectionInternal(connection)
    }
    
    /**
     * 验证 ADB 连接是否可用（内部方法）
     * @return true 表示连接可用，false 表示连接不可用
     */
    private suspend fun verifyConnectionInternal(connection: AdbConnection): Boolean {
        return try {
            val result = connection.executeShell("echo 1", retryOnFailure = false)
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * 验证 Dadb 实例是否可用
     * @return Result.success 表示验证成功，Result.failure 表示验证失败
     */
    private fun verifyDadb(dadb: Dadb): Result<String> {
        LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_VERIFYING.get())
        return try {
            val testResponse = dadb.shell("echo 1")
            if (testResponse.exitCode != 0) {
                throw Exception(BilingualTexts.ERROR_ADB_COMMAND_FAILED.get())
            }
            LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_VERIFY_SUCCESS.get()}")
            Result.success("")
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_VERIFY_FAILED.get()}: ${e.message}")
            try {
                dadb.close()
            } catch (closeException: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CLOSE_DADB_ERROR.get()}: ${closeException.message}")
            }
            
            // 根据异常类型返回不同的错误信息
            val errorMsg = when (e) {
                is java.net.ConnectException -> BilingualTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get()
                is java.io.EOFException -> BilingualTexts.ERROR_ADB_HANDSHAKE_FAILED.get()
                else -> "${BilingualTexts.ERROR_ADB_CONNECTION_UNAVAILABLE.get()}: ${e.message}"
            }
            Result.failure(Exception(errorMsg, e))
        }
    }
    
    /**
     * 获取设备信息
     * @throws Exception 如果连接失败或无法获取设备信息
     */
    private fun getDeviceInfo(dadb: Dadb, deviceId: String, customName: String?, connectionType: ConnectionType): DeviceInfo {
        try {
            val model = dadb.shell("getprop ro.product.model").output.trim()
            val manufacturer = dadb.shell("getprop ro.product.manufacturer").output.trim()
            val androidVersion = dadb.shell("getprop ro.build.version.release").output.trim()
            val serialNumber = dadb.shell("getprop ro.serialno").output.trim()
            
            val displayName = customName ?: "$manufacturer $model"
            
            return DeviceInfo(
                deviceId = deviceId,
                name = displayName,
                model = model,
                manufacturer = manufacturer,
                androidVersion = androidVersion,
                serialNumber = serialNumber,
                connectionType = connectionType
            )
        } catch (e: java.net.ConnectException) {
            // ECONNREFUSED - ADB 连接已断开，需要重新连接
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DISCONNECTED_ECONNREFUSED.get()}: ${e.message}")
            throw Exception(BilingualTexts.ADB_RECONNECT_DEVICE.get(), e)
        } catch (e: java.io.EOFException) {
            // ADB 握手失败或连接中断
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_HANDSHAKE_FAILED_OR_INTERRUPTED.get()}: ${e.message}")
            throw Exception(BilingualTexts.ADB_COMMUNICATION_FAILED.get(), e)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_GET_DEVICE_INFO_FAILED_DETAIL.get()}: ${e.message}", e)
            throw Exception("${BilingualTexts.ADB_CANNOT_GET_DEVICE_INFO.get()}: ${e.message}", e)
        }
    }
    
    /**
     * 断开设备连接
     */
    suspend fun disconnectDevice(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val connection = connectionPool.remove(deviceId)
            if (connection != null) {
                connection.close()
                updateConnectedDevices()
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_DEVICE.get()} $deviceId ${BilingualTexts.ADB_DEVICE_DISCONNECTED.get()}")
                Result.success(true)
            } else {
                Result.failure(Exception(BilingualTexts.ADB_DEVICE_NOT_CONNECTED.get()))
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DISCONNECT_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取设备连接
     */
    fun getConnection(deviceId: String): AdbConnection? {
        return connectionPool[deviceId]
    }

    /**
     * 获取所有已连接设备
     */
    fun getAllConnections(): List<AdbConnection> {
        return connectionPool.values.toList()
    }

    /**
     * 检查设备是否已连接
     */
    fun isDeviceConnected(deviceId: String): Boolean {
        return connectionPool[deviceId]?.isConnected() ?: false
    }
    
    /**
     * 更新已连接设备列表
     */
    private fun updateConnectedDevices() {
        val devices = connectionPool.values.map { it.deviceInfo }
        _connectedDevices.value = devices
    }
    
    /**
     * 断开所有设备（应用退出时调用）
     */
    suspend fun disconnectAll() = withContext(Dispatchers.IO) {
        LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_DISCONNECT_ALL.get())
        
        // 停止心跳任务
        keepAliveJob?.cancel()
        keepAliveJob = null
        
        connectionPool.values.forEach { connection ->
            try {
                connection.close()
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CLOSE_CONNECTION_FAILED.get()}: ${e.message}", e)
            }
        }
        connectionPool.clear()
        updateConnectedDevices()
    }
    
    /**
     * 获取 ADB 密钥对（用于设备扫描等操作）
     */
    fun getKeyPair(): AdbKeyPair? {
        return keyPair
    }
    
    /**
     * 获取公钥（用于手动授权）
     */
    fun getPublicKey(): String? {
        return try {
            val keysDir = File(context.filesDir, "adb_keys")
            val publicKeyFile = File(keysDir, "adbkey.pub")
            if (publicKeyFile.exists()) {
                publicKeyFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_GET_PUBLIC_KEY_FAILED.get()}: ${e.message}", e)
            null
        }
    }
}

/**
 * ADB 连接封装
 */
class AdbConnection(
    val deviceId: String,
    val host: String,
    val port: Int,
    private val dadb: Dadb,
    var deviceInfo: DeviceInfo  // 改为 var 以支持后台更新
) {
    // 端口转发管理（SocketForwarder 或 dadb.tcpForward）
    private val forwarders = ConcurrentHashMap<Int, AutoCloseable>()
    
    /**
     * 检查连接是否有效
     * 注意：这个方法会实际执行 shell 命令，可能较慢
     * 如果只是检查连接对象是否存在，应该检查连接池而不是调用此方法
     */
    fun isConnected(): Boolean {
        return try {
            // 使用更轻量级的命令，或者可以改为检查 dadb 内部状态
            // 但 dadb 没有公开的状态检查方法，所以只能通过实际执行命令来测试
            dadb.shell("echo 1").exitCode == 0
        } catch (_: Exception) {
            false
        }
    }
    
    /**
     * 执行 Shell 命令
     * 
     * 注意：dadb 有自动重连机制（参考 DadbImpl.connection() 方法）
     * - 如果连接已关闭，下次调用 shell() 时会自动创建新连接
     * - 所以如果第一次调用失败，可以尝试重试一次，让 dadb 自动重连
     * - 但如果遇到 ECONNREFUSED，说明远程 ADB 服务已断开，不应重试
     */
    suspend fun executeShell(command: String, retryOnFailure: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = dadb.shell(command)
            Result.success(response.output)
        } catch (e: java.net.ConnectException) {
            // ECONNREFUSED - 远程 ADB 服务已断开，不应重试
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DISCONNECTED_ECONNREFUSED.get()} (ECONNREFUSED)，${BilingualTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command - ${e.message}")
            Result.failure(Exception(BilingualTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
        } catch (e: java.io.EOFException) {
            // ADB 连接已关闭
            // dadb 会在下次调用时自动重连，所以可以重试一次
            if (retryOnFailure) {
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_AUTO_RECONNECT_RETRY.get()}: $command")
                try {
                    delay(100) // 短暂延迟，让 dadb 完成重连
                    val retryResponse = dadb.shell(command)
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_AUTO_RECONNECT_SUCCESS.get()}")
                    Result.success(retryResponse.output)
                } catch (retryException: Exception) {
                    LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_AUTO_RECONNECT_STILL_FAILED.get()}: ${retryException.message}")
                    Result.failure(retryException)
                }
            } else {
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CONNECTION_CLOSED.get()}，${BilingualTexts.ADB_CANNOT_EXECUTE_COMMAND.get()}: $command")
                Result.failure(e)
            }
        } catch (e: java.net.SocketException) {
            // Socket 异常，检查是否是 ECONNREFUSED
            if (e.message?.contains("ECONNREFUSED", ignoreCase = true) == true) {
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_SOCKET_EXCEPTION.get()} (ECONNREFUSED): $command - ${e.message}")
                Result.failure(Exception(BilingualTexts.ERROR_ADB_CONNECTION_DISCONNECTED.get(), e))
            } else if (retryOnFailure) {
                // 其他 Socket 异常，尝试重连
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_SOCKET_EXCEPTION_RETRY.get()}: $command - ${e.message}")
                try {
                    delay(100) // 短暂延迟，让 dadb 完成重连
                    val retryResponse = dadb.shell(command)
                    LogManager.d(LogTags.ADB_CONNECTION, "✓ ${BilingualTexts.ADB_AUTO_RECONNECT_SUCCESS.get()}")
                    Result.success(retryResponse.output)
                } catch (retryException: Exception) {
                    LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_AUTO_RECONNECT_STILL_FAILED.get()}: ${retryException.message}")
                    Result.failure(retryException)
                }
            } else {
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_SOCKET_EXCEPTION.get()}: $command - ${e.message}")
                Result.failure(e)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_EXECUTE_COMMAND_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 异步执行 Shell 命令
     */
    suspend fun executeShellAsync(command: String) = withContext(Dispatchers.IO) {
        try {
            dadb.openShell(command)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_ASYNC_EXECUTE_FAILED.get()}: ${e.message}", e)
        }
    }
    
    /**
     * 打开 Shell 流
     */
    suspend fun openShellStream(command: String): dadb.AdbShellStream? = withContext(Dispatchers.IO) {
        try {
            dadb.openShell(command)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_OPEN_SHELL_STREAM_FAILED.get()}: ${e.message}", e)
            null
        }
    }
    
    /**
     * 设置端口转发
     */
    suspend fun setupPortForward(localPort: Int, remotePort: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 先关闭已存在的转发
            forwarders[localPort]?.close()
            
            val forwarder = dadb.tcpForward(localPort, remotePort)
            forwarders[localPort] = forwarder
            
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_PORT_FORWARD_SUCCESS.get()}: $localPort -> $remotePort")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_PORT_FORWARD_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置 ADB forward（用于 scrcpy socket 连接）
     * 参考 dadb PR #90: 使用自定义 SocketForwarder 直接支持 localabstract socket
     * 1. 使用 SocketForwarder 将本地 LOCAL_PORT 直接转发到设备的 localabstract:NAME
     * 2. 客户端连接到 127.0.0.1:LOCAL_PORT
     * 
     * 优势：减少一层转发，性能更好
     */
    suspend fun setupAdbForward(localPort: Int, socketName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 使用自定义 SocketForwarder，直接支持 localabstract socket
            // 参考 dadb PR #90: Extend tcpForward to support more socket domains
            try {
                // 先关闭已存在的转发
                forwarders[localPort]?.close()
                
                val targetSocket = "localabstract:$socketName"
                val forwarder = SocketForwarder(dadb, localPort, targetSocket)
                forwarder.start()
                forwarders[localPort] = forwarder
                
                LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FORWARD_SETUP_SUCCESS.get()}（${BilingualTexts.LABEL_USING.get()} SocketForwarder: $localPort -> $targetSocket）")
                Result.success(true)
            } catch (e: Exception) {
                LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_SOCKET_FORWARDER_FAILED.get()}: ${e.message}", e)
                Result.failure(e)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FORWARD_SETUP_EXCEPTION.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检查 ADB forward 是否存在且可用
     * 不仅检查 SocketForwarder 状态，还测试端口是否真的可以连接
     */
    suspend fun checkAdbForward(localPort: Int): Boolean = withContext(Dispatchers.IO) {
        // 1. 检查 SocketForwarder 是否存在且运行
        val forwarder = forwarders[localPort] as? SocketForwarder
        if (forwarder?.isRunning() != true) {
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder not Running")
            return@withContext false
        }
        
        // 2. 测试端口是否真的可以连接（快速测试）
        try {
            val testSocket = java.net.Socket()
            testSocket.connect(java.net.InetSocketAddress("127.0.0.1", localPort), 500)
            testSocket.close()
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder can connect")
            return@withContext true
        } catch (_: Exception) {
            // 端口无法连接，说明 forward 虽然在运行但不可用
            LogManager.d(LogTags.ADB_CONNECTION, "forwarder can't connect")
            return@withContext false
        }
    }
    
    /**
     * 移除 ADB forward
     */
    suspend fun removeAdbForward(localPort: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 关闭转发器（SocketForwarder）
            forwarders[localPort]?.close()
            forwarders.remove(localPort)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FORWARD_REMOVED.get()}: tcp:$localPort")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FORWARD_REMOVE_EXCEPTION.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 推送文件
     */
    suspend fun pushFile(localPath: String, remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(localPath)
            dadb.push(file, remotePath)
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FILE_PUSH_SUCCESS.get()}: $localPath -> $remotePath")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FILE_PUSH_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 拉取文件
     */
    suspend fun pullFile(remotePath: String, localPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(localPath)
            dadb.pull(file, remotePath)
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FILE_PULL_SUCCESS.get()}: $remotePath -> $localPath")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_FILE_PULL_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 安装 APK
     */
    suspend fun installApk(apkPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(apkPath)
            dadb.install(file)
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_APK_INSTALL_SUCCESS.get()}: $apkPath")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_APK_INSTALL_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 卸载应用
     */
    suspend fun uninstallPackage(packageName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            dadb.uninstall(packageName)
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_APP_UNINSTALL_SUCCESS.get()}: $packageName")
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_APP_UNINSTALL_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 推送 scrcpy-server.jar 到设备
     * @param context Android Context，用于访问 assets
     * @param scrcpyServerPath 远程路径，默认 /data/local/tmp/scrcpy-server.jar
     */
    suspend fun pushScrcpyServer(context: Context, scrcpyServerPath: String = "/data/local/tmp/scrcpy-server.jar"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            try {
                context.assets.open("scrcpy-server.jar").use { input ->
                    val tempFile = context.cacheDir.resolve("scrcpy-server.jar")
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    
                    val pushResult = pushFile(tempFile.absolutePath, scrcpyServerPath)
                    if (pushResult.isFailure) {
                        return@withContext pushResult
                    }
                    
                    executeShell("chmod 755 $scrcpyServerPath")
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception(BilingualTexts.ADB_SCRCPY_SERVER_NOT_IN_ASSETS.get() + ": ${e.message}"))
            }
            
            Result.success(true)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_PUSH_SCRCPY_SERVER_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检测可用的视频编码器
     * 启动 scrcpy-server 并传入 list_encoders=true 参数，读取设备的编码器列表
     * @param context Android Context，用于推送 scrcpy-server.jar（如果需要）
     */
    suspend fun detectVideoEncoders(context: Context): Result<List<VideoEncoderInfo>> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_DETECTING_VIDEO_ENCODERS.get())
            
            // 自动推送 scrcpy-server.jar（如果不存在）
            val pushResult = pushScrcpyServer(context, AppConstants.SCRCPY_SERVER_PATH)
            if (pushResult.isFailure) {
                LogManager.e(LogTags.ADB_CONNECTION, BilingualTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
                return@withContext Result.failure(pushResult.exceptionOrNull() ?: Exception(BilingualTexts.ADB_PUSH_FAILED.get()))
            }
            
            // 启动 scrcpy-server 并传入 list_encoders=true 参数
            val command = com.mobile.scrcpy.android.feature.scrcpy.ScrcpyClient.buildScrcpyServerCommand("list_encoders=true")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
            
            // 使用 openShellStream 读取输出
            val shellStream = openShellStream(command)
            if (shellStream == null) {
                LogManager.e(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
                return@withContext Result.failure(Exception(BilingualTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get()))
            }
            
            val output = StringBuilder()
            var lineCount = 0
            val maxLines = 200 // 最多读取 200 行
            
            try {
                while (lineCount < maxLines) {
                    when (val packet = shellStream.read()) {
                        is dadb.AdbShellPacket.StdOut -> {
                            val text = String(packet.payload, Charsets.UTF_8)
                            output.append(text)
                            lineCount++
                            
                            // 如果读到了编码器列表的结束标志，可以提前退出
                            if (text.contains("List of audio encoders:")) {
                                // 继续读取音频编码器部分
                                repeat(50) {
                                    val audioPacket = shellStream.read()
                                    if (audioPacket is dadb.AdbShellPacket.StdOut) {
                                        output.append(String(audioPacket.payload, Charsets.UTF_8))
                                    }
                                }
                                break
                            }
                        }
                        is dadb.AdbShellPacket.Exit -> {
                            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_SHELL_STREAM_EXIT.get())
                            break
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_READ_OUTPUT_ERROR.get()}: ${e.message}")
            } finally {
                shellStream.close()
            }
            
            val outputText = output.toString()
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_RECEIVED_OUTPUT.get()} (${outputText.length} ${BilingualTexts.LABEL_CHARACTERS.get()})")
            
            // 解析输出
            val encoders = parseEncoderList(outputText)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DETECTED_VIDEO_ENCODERS.get()} ${encoders.size} ${BilingualTexts.LABEL_ITEMS.get()}")
            if (encoders.isEmpty()) {
                LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_NO_ENCODERS_DETECTED_OUTPUT.get()}：\n$outputText")
            }
            Result.success(encoders)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DETECT_ENCODERS_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检测音频编码器
     */
    suspend fun detectAudioEncoders(context: Context): Result<List<AudioEncoderInfo>> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_DETECTING_AUDIO_ENCODERS.get())
            
            // 自动推送 scrcpy-server.jar（如果不存在）
            val pushResult = pushScrcpyServer(context, AppConstants.SCRCPY_SERVER_PATH)
            if (pushResult.isFailure) {
                LogManager.e(LogTags.ADB_CONNECTION, BilingualTexts.ADB_PUSH_SERVER_FAILED_CANNOT_DETECT.get())
                return@withContext Result.failure(pushResult.exceptionOrNull() ?: Exception(BilingualTexts.ADB_PUSH_FAILED.get()))
            }
            
            // 启动 scrcpy-server 并传入 list_encoders=true 参数
            val command = com.mobile.scrcpy.android.feature.scrcpy.ScrcpyClient.buildScrcpyServerCommand("list_encoders=true")
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_EXECUTE_COMMAND.get()}: $command")
            
            // 使用 openShellStream 读取输出
            val shellStream = openShellStream(command)
            if (shellStream == null) {
                LogManager.e(LogTags.ADB_CONNECTION, BilingualTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get())
                return@withContext Result.failure(Exception(BilingualTexts.ADB_CANNOT_OPEN_SHELL_STREAM.get()))
            }
            
            val output = StringBuilder()
            var lineCount = 0
            val maxLines = 200
            
            try {
                while (lineCount < maxLines) {
                    when (val packet = shellStream.read()) {
                        is dadb.AdbShellPacket.StdOut -> {
                            val text = String(packet.payload, Charsets.UTF_8)
                            output.append(text)
                            lineCount++
                            
                            if (text.contains("List of audio encoders:")) {
                                repeat(50) {
                                    val audioPacket = shellStream.read()
                                    if (audioPacket is dadb.AdbShellPacket.StdOut) {
                                        output.append(String(audioPacket.payload, Charsets.UTF_8))
                                    }
                                }
                                break
                            }
                        }
                        is dadb.AdbShellPacket.Exit -> {
                            LogManager.d(LogTags.ADB_CONNECTION, BilingualTexts.ADB_SHELL_STREAM_EXIT.get())
                            break
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_READ_OUTPUT_ERROR.get()}: ${e.message}")
            } finally {
                shellStream.close()
            }
            
            val outputText = output.toString()
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.LABEL_RECEIVED_OUTPUT.get()} (${outputText.length} ${BilingualTexts.LABEL_CHARACTERS.get()})")
            
            // 解析输出
            val encoders = parseAudioEncoderList(outputText)
            
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DETECTED_AUDIO_ENCODERS.get()} ${encoders.size} ${BilingualTexts.LABEL_ITEMS.get()}")
            if (encoders.isEmpty()) {
                LogManager.w(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_NO_AUDIO_ENCODERS_DETECTED_OUTPUT.get()}：\n$outputText")
            }
            Result.success(encoders)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_DETECT_AUDIO_ENCODERS_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 解析 scrcpy-server 输出的编码器列表
     * 格式示例：
     * List of video encoders:
     *     --video-codec=h264 --video-encoder='c2.android.avc.encoder'       (hw)
     *     --video-codec=h265 --video-encoder='c2.qti.hevc.encoder'          (hw) [vendor]
     */
    private fun parseEncoderList(output: String): List<VideoEncoderInfo> {
        val encoders = mutableListOf<VideoEncoderInfo>()
        
        // 只解析视频编码器部分（在 "List of video encoders:" 和 "List of audio encoders:" 之间）
        val videoSection = if (output.contains("List of video encoders:")) {
            val start = output.indexOf("List of video encoders:")
            val end = if (output.contains("List of audio encoders:")) {
                output.indexOf("List of audio encoders:")
            } else {
                output.length
            }
            output.substring(start, end)
        } else {
            output
        }
        
        val lines = videoSection.lines()
        for (line in lines) {
            val trimmed = line.trim()
            
            // 匹配 --video-encoder=xxx 或 --video-encoder='xxx' 格式
            val encoderMatch = Regex("--video-encoder='?([^'\\s]+)'?").find(trimmed)
            val codecMatch = Regex("--video-codec=(\\w+)").find(trimmed)
            
            if (encoderMatch != null) {
                // 去掉引号
                val encoderName = encoderMatch.groupValues[1].trim('\'')
                val codecName = codecMatch?.groupValues?.get(1) ?: "unknown"
                
                // 推断 MIME 类型（使用 ApiCompatHelper 处理兼容性）
                val mimeType = when (codecName.lowercase()) {
                    "h264" -> "video/avc"
                    "h265" -> "video/hevc"
                    "h263" -> "video/3gpp"
                    "av1" -> {
                        // AV1 需要 API 29+，低版本设备跳过
                        if (ApiCompatHelper.isAV1Supported()) {
                            "video/av01"
                        } else {
                            null  // 不支持的编解码器返回 null，后续过滤
                        }
                    }
                    "vp8" -> "video/x-vnd.on2.vp8"
                    "vp9" -> "video/x-vnd.on2.vp9"
                    "mpeg4" -> "video/mp4v-es"
                    else -> "video/$codecName"
                }
                
                // 只添加支持的编解码器
                if (mimeType != null) {
                    encoders.add(VideoEncoderInfo(encoderName, mimeType))
                }
            }
        }
        
        return encoders
    }
    
    /**
     * 解析音频编码器列表
     * 格式示例：
     * List of audio encoders:
     *     --audio-codec=opus --audio-encoder='c2.android.opus.encoder'
     *     --audio-codec=aac --audio-encoder='c2.android.aac.encoder'
     */
    private fun parseAudioEncoderList(output: String): List<AudioEncoderInfo> {
        val encoders = mutableListOf<AudioEncoderInfo>()
        
        // 只解析音频编码器部分（在 "List of audio encoders:" 之后）
        val audioSection = if (output.contains("List of audio encoders:")) {
            val start = output.indexOf("List of audio encoders:")
            output.substring(start)
        } else {
            return encoders
        }
        
        val lines = audioSection.lines()
        for (line in lines) {
            val trimmed = line.trim()
            
            // 匹配 --audio-encoder=xxx 或 --audio-encoder='xxx' 格式
            val encoderMatch = Regex("--audio-encoder='?([^'\\s]+)'?").find(trimmed)
            val codecMatch = Regex("--audio-codec=(\\w+)").find(trimmed)
            
            if (encoderMatch != null) {
                val encoderName = encoderMatch.groupValues[1].trim('\'')
                val codecName = codecMatch?.groupValues?.get(1) ?: "unknown"
                
                // 推断 MIME 类型
                val mimeType = when (codecName.lowercase()) {
                    "opus" -> "audio/opus"
                    "aac" -> "audio/mp4a-latm"
                    "flac" -> "audio/flac"
                    "raw" -> "audio/raw"
                    "3gpp", "amrnb" -> "audio/3gpp"
                    "amrwb" -> "audio/amr-wb"
                    else -> "audio/$codecName"
                }
                
                encoders.add(AudioEncoderInfo(encoderName, mimeType))
            }
        }
        
        return encoders
    }

    /**
     * 关闭连接
     */
    fun close() {
        try {
            // 关闭所有端口转发
            forwarders.values.forEach { it.close() }
            forwarders.clear()
            
            // 关闭 ADB 连接
            dadb.close()
            LogManager.d(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CONNECTION_CLOSED.get()}: $deviceId")
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_CONNECTION, "${BilingualTexts.ADB_CLOSE_CONNECTION_FAILED_DETAIL.get()}: ${e.message}", e)
        }
    }
}

/**
 * 视频编码器信息
 */
data class VideoEncoderInfo(
    override val name: String,
    override val mimeType: String
) : com.mobile.scrcpy.android.ui.components.EncoderInfo

/**
 * 音频编码器信息
 */
data class AudioEncoderInfo(
    override val name: String,
    override val mimeType: String
) : com.mobile.scrcpy.android.ui.components.EncoderInfo

/**
 * 设备信息
 */
data class DeviceInfo(
    val deviceId: String,
    val name: String,
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val serialNumber: String,
    val connectionType: ConnectionType = ConnectionType.TCP
)
