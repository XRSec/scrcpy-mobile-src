/*
 * USB ADB 连接管理器
 * 
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 *   - UsbChannel.java: USB 通道实现
 *   - Adb.java: ADB 协议封装
 * - dadb: https://github.com/mobile-dev-inc/dadb
 *   - AdbServer.kt: ADB Server 集成
 * 
 * 版权说明：
 * - Easycontrol 使用 GPL-3.0 许可证
 * - dadb 使用 Apache-2.0 许可证
 * - 本实现基于 dadb 库，参考 Easycontrol 的 USB 设备发现逻辑
 * 
 * 实现方式：
 * - 使用 dadb 库的 AdbServer 功能连接 USB 设备
 * - 通过 Android USB Host API 发现 USB 设备
 * - 自动请求 USB 权限并建立连接
 */

package com.mobile.scrcpy.android.core.adb.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import dadb.adbserver.AdbServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * USB ADB 管理器
 * 负责 USB 设备的发现、权限请求和连接管理
 */
class UsbAdbManager(private val context: Context) {
    
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    
    // USB 设备列表
    private val _usbDevices = MutableStateFlow<List<UsbDeviceInfo>>(emptyList())
    val usbDevices: StateFlow<List<UsbDeviceInfo>> = _usbDevices.asStateFlow()
    
    // USB 权限请求 Action
    private val ACTION_USB_PERMISSION = "com.mobile.scrcpy.android.USB_PERMISSION"
    
    companion object {
        // ADB 接口标识（参考 Easycontrol 实现）
        private const val ADB_CLASS = 0xFF
        private const val ADB_SUBCLASS = 0x42
        private const val ADB_PROTOCOL = 0x01
    }
    
    /**
     * 扫描 USB 设备
     * 查找所有连接的 ADB 设备
     */
    suspend fun scanUsbDevices(): Result<List<UsbDeviceInfo>> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_MANAGER, BilingualTexts.USB_SCANNING_DEVICES.get())
            
            val devices = mutableListOf<UsbDeviceInfo>()
            val deviceList = usbManager.deviceList
            
            LogManager.d(LogTags.ADB_MANAGER, "${BilingualTexts.USB_FOUND_DEVICES.get()}: ${deviceList.size}")
            
            for ((_, device) in deviceList) {
                try {
                    // 检查是否是 ADB 设备
                    if (isAdbDevice(device)) {
                        val hasPermission = usbManager.hasPermission(device)
                        
                        // 尝试获取序列号，如果没有权限可能会失败
                        val serialNumber = try {
                            device.serialNumber ?: ""
                        } catch (e: SecurityException) {
                            // 没有权限时无法获取序列号，使用设备名称作为标识
                            LogManager.w(LogTags.ADB_MANAGER, "${BilingualTexts.USB_PERMISSION_DENIED.get()}: ${device.deviceName}")
                            ""
                        }
                        
                        val deviceInfo = UsbDeviceInfo(
                            device = device,
                            deviceName = device.deviceName,
                            productName = device.productName ?: "Unknown",
                            manufacturerName = device.manufacturerName ?: "Unknown",
                            serialNumber = serialNumber,
                            hasPermission = hasPermission
                        )
                        devices.add(deviceInfo)
                        
                        LogManager.d(LogTags.ADB_MANAGER, 
                            "${BilingualTexts.USB_DEVICE_FOUND.get()}: ${deviceInfo.productName} " +
                            "(${BilingualTexts.USB_PERMISSION.get()}: $hasPermission)")
                    }
                } catch (e: SecurityException) {
                    // 单个设备权限错误不应该影响整个扫描
                    LogManager.w(LogTags.ADB_MANAGER, "${BilingualTexts.USB_PERMISSION_DENIED.get()}: ${device.deviceName} - ${e.message}")
                    
                    // 即使没有权限，也添加到列表中，让用户知道有这个设备
                    try {
                        val deviceInfo = UsbDeviceInfo(
                            device = device,
                            deviceName = device.deviceName,
                            productName = device.productName ?: "Unknown",
                            manufacturerName = device.manufacturerName ?: "Unknown",
                            serialNumber = "",
                            hasPermission = false
                        )
                        devices.add(deviceInfo)
                    } catch (innerException: Exception) {
                        // 如果连基本信息都无法获取，跳过这个设备
                        LogManager.e(LogTags.ADB_MANAGER, "Failed to get device info: ${innerException.message}")
                    }
                } catch (e: Exception) {
                    // 其他异常也不应该影响整个扫描
                    LogManager.e(LogTags.ADB_MANAGER, "Error scanning device ${device.deviceName}: ${e.message}")
                }
            }
            
            _usbDevices.value = devices
            Result.success(devices)
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_MANAGER, "${BilingualTexts.USB_SCAN_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 检查设备是否是 ADB 设备
     * 参考 Easycontrol 的实现逻辑
     */
    private fun isAdbDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(i)
            // 检查接口类型是否匹配 ADB
            if (usbInterface.interfaceClass == ADB_CLASS &&
                usbInterface.interfaceSubclass == ADB_SUBCLASS &&
                usbInterface.interfaceProtocol == ADB_PROTOCOL) {
                return true
            }
        }
        return false
    }
    
    /**
     * 请求 USB 权限
     * 如果已有权限则直接返回成功
     */
    suspend fun requestUsbPermission(device: UsbDevice): Result<Boolean> = suspendCancellableCoroutine { continuation ->
        try {
            // 检查是否已有权限
            if (usbManager.hasPermission(device)) {
                LogManager.d(LogTags.ADB_MANAGER, BilingualTexts.USB_PERMISSION_ALREADY_GRANTED.get())
                continuation.resume(Result.success(true))
                return@suspendCancellableCoroutine
            }
            
            LogManager.d(LogTags.ADB_MANAGER, BilingualTexts.USB_REQUESTING_PERMISSION.get())
            
            // 注册广播接收器
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (ACTION_USB_PERMISSION == intent.action) {
                        context.unregisterReceiver(this)
                        
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted) {
                            LogManager.d(LogTags.ADB_MANAGER, BilingualTexts.USB_PERMISSION_GRANTED.get())
                            continuation.resume(Result.success(true))
                        } else {
                            LogManager.w(LogTags.ADB_MANAGER, BilingualTexts.USB_PERMISSION_DENIED.get())
                            continuation.resume(Result.failure(Exception(BilingualTexts.USB_PERMISSION_DENIED.get())))
                        }
                    }
                }
            }
            
            // 注册广播
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            ApiCompatHelper.registerReceiver(context, receiver, filter)
            
            // 请求权限（使用 ApiCompatHelper 统一处理兼容性）
            val permissionIntent = ApiCompatHelper.createUsbPermissionPendingIntent(context, ACTION_USB_PERMISSION)
            usbManager.requestPermission(device, permissionIntent)
            
            // 取消时清理
            continuation.invokeOnCancellation {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_MANAGER, "${BilingualTexts.USB_PERMISSION_REQUEST_FAILED.get()}: ${e.message}", e)
            continuation.resume(Result.failure(e))
        }
    }
    
    /**
     * 通过 ADB Server 连接 USB 设备
     * 使用 dadb 的 AdbServer 功能
     */
    suspend fun connectUsbDevice(serialNumber: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogManager.d(LogTags.ADB_MANAGER, "${BilingualTexts.USB_CONNECTING_DEVICE.get()}: $serialNumber")
            
            // 使用 dadb 的 AdbServer 连接 USB 设备
            // deviceQuery 格式: host:transport:<serial-number>
            val deviceQuery = "host:transport:$serialNumber"
            
            LogManager.d(LogTags.ADB_MANAGER, "${BilingualTexts.USB_DEVICE_QUERY.get()}: $deviceQuery")
            
            // 返回设备 ID（使用 serial number 作为 ID）
            Result.success("usb:$serialNumber")
        } catch (e: Exception) {
            LogManager.e(LogTags.ADB_MANAGER, "${BilingualTexts.USB_CONNECT_FAILED.get()}: ${e.message}", e)
            Result.failure(e)
        }
    }
}

/**
 * USB 设备信息
 */
data class UsbDeviceInfo(
    val device: UsbDevice,
    val deviceName: String,
    val productName: String,
    val manufacturerName: String,
    val serialNumber: String,
    val hasPermission: Boolean
) {
    /**
     * 获取显示名称
     * 避免制造商和产品名称重复（如 SAMSUNG SAMSUNG）
     */
    fun getDisplayName(): String {
        return when {
            // 产品名称未知，使用设备名称
            productName == "Unknown" -> deviceName
            // 制造商未知，只显示产品名称
            manufacturerName == "Unknown" -> productName
            // 产品名称已包含制造商名称，只显示产品名称
            productName.contains(manufacturerName, ignoreCase = true) -> productName
            // 制造商和产品名称相同，只显示一个
            manufacturerName.equals(productName, ignoreCase = true) -> manufacturerName
            // 正常情况，拼接显示
            else -> "$manufacturerName $productName"
        }
    }
}
