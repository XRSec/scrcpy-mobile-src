/*
 * USB ADB 通道实现
 * 
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 *   - UsbChannel.java: USB 通道实现
 * - flashbot: https://github.com/wuxudong/flashbot
 *   - UsbChannel.java: USB ADB 通道
 * 
 * 版权说明：
 * - Easycontrol 使用 GPL-3.0 许可证
 * - 本实现参考其 USB 通道逻辑，使用 Kotlin 重写
 */

package com.mobile.scrcpy.android.core.adb.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * USB ADB 通道
 * 实现 USB 设备的 ADB 协议通信
 */
class UsbAdbChannel(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice
) : AutoCloseable {
    
    private var usbConnection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    
    // 读取缓冲区
    private val readBuffer = LinkedBlockingQueue<ByteBuffer>()
    private var readThread: Thread? = null
    private val requestPool = mutableListOf<UsbRequest>()
    
    @Volatile
    private var closed = false
    
    companion object {
        // ADB 接口标识
        private const val ADB_CLASS = 0xFF
        private const val ADB_SUBCLASS = 0x42
        private const val ADB_PROTOCOL = 0x01
        
        // ADB 协议头部长度
        private const val ADB_HEADER_LENGTH = 24
        
        // 超时时间
        private const val TRANSFER_TIMEOUT_MS = 5000
    }
    
    init {
        connect()
    }
    
    /**
     * 连接 USB 设备
     */
    private fun connect() {
        // 打开 USB 设备连接
        usbConnection = usbManager.openDevice(usbDevice)
            ?: throw IOException("Failed to open USB device")
        
        // 查找 ADB 接口
        for (i in 0 until usbDevice.interfaceCount) {
            val iface = usbDevice.getInterface(i)
            if (iface.interfaceClass == ADB_CLASS &&
                iface.interfaceSubclass == ADB_SUBCLASS &&
                iface.interfaceProtocol == ADB_PROTOCOL) {
                usbInterface = iface
                break
            }
        }
        
        val iface = usbInterface ?: throw IOException("ADB interface not found")
        
        // 独占接口
        if (!usbConnection!!.claimInterface(iface, true)) {
            throw IOException("Failed to claim USB interface")
        }
        
        // 查找输入输出端点
        for (i in 0 until iface.endpointCount) {
            val endpoint = iface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                when (endpoint.direction) {
                    UsbConstants.USB_DIR_OUT -> endpointOut = endpoint
                    UsbConstants.USB_DIR_IN -> endpointIn = endpoint
                }
            }
        }
        
        if (endpointIn == null || endpointOut == null) {
            throw IOException("USB endpoints not found")
        }
        
        // 启动读取线程
        startReadThread()
        
        LogManager.d(LogTags.ADB_CONNECTION, "USB ADB channel connected")
    }
    
    /**
     * 写入数据
     * 
     * 注意：ADB 通过 USB 连接时必须头部和载荷分开发送
     */
    fun write(data: ByteBuffer) {
        if (closed) throw IOException("Channel is closed")
        
        val connection = usbConnection ?: throw IOException("USB connection is null")
        val endpoint = endpointOut ?: throw IOException("USB endpoint out is null")
        
        while (data.remaining() > 0) {
            // 读取头部（24 字节）
            val header = ByteArray(ADB_HEADER_LENGTH)
            data.get(header)
            
            // 发送头部
            val headerSent = connection.bulkTransfer(endpoint, header, header.size, TRANSFER_TIMEOUT_MS)
            if (headerSent != header.size) {
                throw IOException("Failed to send ADB header: sent $headerSent bytes")
            }
            
            // 读取载荷长度（头部偏移 12 的 4 字节）
            val payloadLength = ByteBuffer.wrap(header)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt(12)
            
            // 发送载荷
            if (payloadLength > 0) {
                val payload = ByteArray(payloadLength)
                data.get(payload)
                
                val payloadSent = connection.bulkTransfer(endpoint, payload, payload.size, TRANSFER_TIMEOUT_MS)
                if (payloadSent != payload.size) {
                    throw IOException("Failed to send ADB payload: sent $payloadSent bytes")
                }
            }
        }
    }
    
    /**
     * 读取数据
     */
    fun read(size: Int, timeout: Long = 5000): ByteBuffer {
        if (closed) throw IOException("Channel is closed")
        
        val buffer = readBuffer.poll(timeout, TimeUnit.MILLISECONDS)
            ?: throw IOException("Read timeout")
        
        if (buffer.remaining() < size) {
            throw IOException("Insufficient data: expected $size, got ${buffer.remaining()}")
        }
        
        return buffer
    }
    
    /**
     * 启动读取线程
     */
    private fun startReadThread() {
        readThread = Thread {
            try {
                while (!closed && !Thread.currentThread().isInterrupted) {
                    // 读取头部
                    val header = readRequest(ADB_HEADER_LENGTH)
                    if (header.remaining() < ADB_HEADER_LENGTH) {
                        LogManager.e(LogTags.ADB_CONNECTION, "Invalid ADB header size")
                        break
                    }
                    
                    header.order(ByteOrder.LITTLE_ENDIAN)
                    readBuffer.offer(header)
                    
                    // 读取载荷长度
                    val payloadLength = header.getInt(12)
                    
                    // 读取载荷
                    if (payloadLength > 0) {
                        val payload = readRequest(payloadLength)
                        readBuffer.offer(payload)
                    }
                }
            } catch (e: Exception) {
                if (!closed) {
                    LogManager.e(LogTags.ADB_CONNECTION, "USB read thread error: ${e.message}")
                }
            } finally {
                // 通知读取结束
                readBuffer.offer(ByteBuffer.allocate(0))
            }
        }.apply {
            name = "USB-ADB-Read"
            start()
        }
    }
    
    /**
     * 读取指定长度的数据
     */
    private fun readRequest(length: Int): ByteBuffer {
        val connection = usbConnection ?: throw IOException("USB connection is null")
        val endpoint = endpointIn ?: throw IOException("USB endpoint in is null")
        
        // 获取或创建 UsbRequest
        val request = if (requestPool.isNotEmpty()) {
            requestPool.removeAt(0)
        } else {
            UsbRequest().apply {
                initialize(connection, endpoint)
            }
        }
        
        val buffer = ByteBuffer.allocate(length)
        request.clientData = buffer
        
        // 加入异步请求队列
        if (!request.queue(buffer)) {
            throw IOException("Failed to queue USB request")
        }
        
        // 等待请求完成
        while (true) {
            val completedRequest = connection.requestWait()
                ?: throw IOException("USB requestWait returned null")
            
            if (completedRequest.endpoint == endpoint) {
                val clientData = completedRequest.clientData as? ByteBuffer
                
                // 回收 request
                requestPool.add(request)
                
                if (clientData == buffer) {
                    buffer.flip()
                    return buffer
                }
            }
        }
    }
    
    /**
     * 关闭通道
     */
    override fun close() {
        if (closed) return
        closed = true
        
        // 停止读取线程
        readThread?.interrupt()
        
        try {
            // 清理 UsbRequest
            requestPool.forEach { it.close() }
            requestPool.clear()
            
            // 释放接口
            usbInterface?.let { iface ->
                usbConnection?.releaseInterface(iface)
            }
            
            // 关闭连接
            usbConnection?.close()
        } catch (e: Exception) {
            LogManager.w(LogTags.ADB_CONNECTION, "Error closing USB channel: ${e.message}")
        }
        
        LogManager.d(LogTags.ADB_CONNECTION, "USB ADB channel closed")
    }
}
