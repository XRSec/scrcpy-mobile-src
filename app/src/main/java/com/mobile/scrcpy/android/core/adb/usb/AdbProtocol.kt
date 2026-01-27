/*
 * ADB 协议实现
 * 
 * 参考实现：
 * - Easycontrol: https://github.com/Chenyqiang/Easycontrol
 * - adblib: https://github.com/tananaev/adblib
 * - dadb: https://github.com/mobile-dev-inc/dadb
 * - ADB 协议文档: https://github.com/cstyan/adbDocumentation
 */

package com.mobile.scrcpy.android.core.adb.usb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * ADB 协议常量和消息生成
 */
object AdbProtocol {
    
    // 协议常量
    const val ADB_HEADER_LENGTH = 24
    
    // 认证类型
    const val AUTH_TYPE_TOKEN = 1
    const val AUTH_TYPE_SIGNATURE = 2
    const val AUTH_TYPE_RSA_PUBLIC = 3
    
    // 命令类型
    const val CMD_AUTH = 0x48545541  // "AUTH"
    const val CMD_CNXN = 0x4e584e43  // "CNXN"
    const val CMD_OPEN = 0x4e45504f  // "OPEN"
    const val CMD_OKAY = 0x59414b4f  // "OKAY"
    const val CMD_CLSE = 0x45534c43  // "CLSE"
    const val CMD_WRTE = 0x45545257  // "WRTE"
    
    // 连接参数
    const val CONNECT_VERSION = 0x01000000
    
    /**
     * 最大数据大小
     * 
     * 设置为 15KB 而不是 1MB 的原因：
     * - 某些设备 USB 仅支持最大 16KB
     * - 如果 ADB 使用过大的数据，会导致 USB 无法传输，丢失数据
     * - 旧版本 adb 服务端硬编码 maxdata=4096，如果设备太老可以尝试修改为 4096
     */
    const val CONNECT_MAXDATA = 15 * 1024
    
    // 连接载荷
    private val CONNECT_PAYLOAD = "host::\u0000".toByteArray(StandardCharsets.UTF_8)
    
    /**
     * 生成 CNXN 消息（连接）
     */
    fun generateConnect(): ByteBuffer {
        return generateMessage(CMD_CNXN, CONNECT_VERSION, CONNECT_MAXDATA, CONNECT_PAYLOAD)
    }
    
    /**
     * 生成 AUTH 消息（认证）
     */
    fun generateAuth(type: Int, data: ByteArray): ByteBuffer {
        return generateMessage(CMD_AUTH, type, 0, data)
    }
    
    /**
     * 生成 OPEN 消息（打开流）
     */
    fun generateOpen(localId: Int, destination: String): ByteBuffer {
        val payload = (destination + "\u0000").toByteArray(StandardCharsets.UTF_8)
        return generateMessage(CMD_OPEN, localId, 0, payload)
    }
    
    /**
     * 生成 WRITE 消息（写入数据）
     */
    fun generateWrite(localId: Int, remoteId: Int, data: ByteArray): ByteBuffer {
        return generateMessage(CMD_WRTE, localId, remoteId, data)
    }
    
    /**
     * 生成 CLOSE 消息（关闭流）
     */
    fun generateClose(localId: Int, remoteId: Int): ByteBuffer {
        return generateMessage(CMD_CLSE, localId, remoteId, null)
    }
    
    /**
     * 生成 OKAY 消息（确认）
     */
    fun generateOkay(localId: Int, remoteId: Int): ByteBuffer {
        return generateMessage(CMD_OKAY, localId, remoteId, null)
    }
    
    /**
     * 生成 ADB 消息
     */
    private fun generateMessage(
        cmd: Int,
        arg0: Int,
        arg1: Int,
        payload: ByteArray?
    ): ByteBuffer {
        val size = if (payload == null) {
            ADB_HEADER_LENGTH
        } else {
            ADB_HEADER_LENGTH + payload.size
        }
        
        val buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        
        // 命令
        buffer.putInt(cmd)
        // 参数 0
        buffer.putInt(arg0)
        // 参数 1
        buffer.putInt(arg1)
        
        // 载荷长度和校验和
        if (payload == null) {
            buffer.putInt(0)
            buffer.putInt(0)
        } else {
            buffer.putInt(payload.size)
            buffer.putInt(payloadChecksum(payload))
        }
        
        // 魔数（命令的按位取反）
        buffer.putInt(cmd.inv())
        
        // 载荷
        if (payload != null) {
            buffer.put(payload)
        }
        
        buffer.flip()
        return buffer
    }
    
    /**
     * 计算载荷校验和
     */
    private fun payloadChecksum(payload: ByteArray): Int {
        var checksum = 0
        for (b in payload) {
            checksum += (b.toInt() and 0xFF)
        }
        return checksum
    }
    
    /**
     * ADB 消息
     */
    data class AdbMessage(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val payloadLength: Int,
        val payload: ByteBuffer?
    ) {
        companion object {
            /**
             * 从通道解析 ADB 消息
             */
            fun parse(channel: UsbAdbChannel): AdbMessage {
                // 读取头部
                val header = channel.read(ADB_HEADER_LENGTH)
                header.order(ByteOrder.LITTLE_ENDIAN)
                
                val command = header.getInt()
                val arg0 = header.getInt()
                val arg1 = header.getInt()
                val payloadLength = header.getInt()
                // 跳过校验和和魔数
                header.getInt()
                header.getInt()
                
                // 读取载荷
                val payload = if (payloadLength > 0) {
                    channel.read(payloadLength)
                } else {
                    null
                }
                
                return AdbMessage(command, arg0, arg1, payloadLength, payload)
            }
        }
        
        /**
         * 获取命令名称（用于调试）
         */
        fun getCommandName(): String {
            return when (command) {
                CMD_AUTH -> "AUTH"
                CMD_CNXN -> "CNXN"
                CMD_OPEN -> "OPEN"
                CMD_OKAY -> "OKAY"
                CMD_CLSE -> "CLSE"
                CMD_WRTE -> "WRTE"
                else -> "UNKNOWN(0x${command.toString(16)})"
            }
        }
    }
}
