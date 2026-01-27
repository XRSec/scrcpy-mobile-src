package com.mobile.scrcpy.android.core.data.model

import com.mobile.scrcpy.android.common.NetworkConstants
import com.mobile.scrcpy.android.common.FilePathConstants.DEFAULT_FILE_TRANSFER_PATH

data class ScrcpySession(
    val id: String,
    val name: String,
    val color: SessionColor,
    val deviceId: String? = null,  // 关联的设备 ID
    val isConnected: Boolean = false,
    val hasWifi: Boolean = false,
    val hasWarning: Boolean = false
)

enum class SessionColor {
    BLUE, RED, GREEN, ORANGE, PURPLE
}

data class ScrcpyAction(
    val id: String,
    val name: String,
    val type: ActionType,
    val commands: List<String>
)

enum class ActionType {
    CONVERSATION, AUTOMATION
}

enum class ThemeMode {
    SYSTEM, DARK, LIGHT
}

enum class AppLanguage {
    AUTO,      // 跟随系统
    CHINESE,   // 中文
    ENGLISH    // English
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.AUTO,
    val keepAliveMinutes: Int = 5,
    val showOnLockScreen: Boolean = false,
    val enableActivityLog: Boolean = true,
    val fileTransferPath: String = DEFAULT_FILE_TRANSFER_PATH,
    val enableFloatingMenu: Boolean = true,  // 悬浮球开关
    val enableFloatingHapticFeedback: Boolean = true  // 悬浮球触感反馈
)

// 设备连接类型
enum class ConnectionType {
    TCP,    // TCP/IP 网络连接
    USB     // USB 有线连接
}

// 设备连接配置
data class DeviceConfig(
    val deviceId: String,
    val host: String,
    val port: Int = NetworkConstants.DEFAULT_ADB_PORT_INT,
    val customName: String? = null,
    val autoConnect: Boolean = false,
    val codecCache: CodecCache? = null,  // 编解码器缓存
    val connectionType: ConnectionType = ConnectionType.TCP  // 连接类型
)

// 编解码器缓存（避免每次启动都检测）
data class CodecCache(
    val videoDecoderName: String? = null,  // 视频解码器名称
    val audioDecoderName: String? = null,  // 音频解码器名称
    val lastUpdated: Long = System.currentTimeMillis()  // 最后更新时间
) {
    companion object {
        const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L  // 7天有效期
    }
    
    /**
     * 检查缓存是否有效
     */
    fun isValid(): Boolean {
        return System.currentTimeMillis() - lastUpdated < CACHE_VALIDITY_MS
    }
}

// ============ 扩展函数：数据转换 ============

/**
 * 解析 maxSize 字符串为整数
 * 
 * 规则：
 * - 空字符串 "" -> null（不限制分辨率）
 * - "0" -> null（不限制分辨率）
 * - 有效数字 -> 返回该数字
 * - 无效输入 -> null（不限制分辨率）
 * 
 * @return Int? - null 表示不限制，否则返回具体数值
 */
fun String.parseMaxSize(): Int? {
    return when {
        this.isEmpty() -> null  // 空字符串表示不限制
        this == "0" -> null     // 0 表示不限制
        else -> this.toIntOrNull()?.takeIf { it > 0 }  // 有效数字或 null
    }
}

/**
 * 将 maxSize 整数转换为字符串（用于存储）
 * 
 * @return String - null 转为空字符串 ""，其他转为字符串
 */
fun Int?.toMaxSizeString(): String {
    return this?.toString() ?: ""
}

// ============ 连接进度状态 ============

/**
 * 连接步骤
 */
enum class ConnectionStep {
    ADB_CONNECT,      // ADB 连接
    ADB_FORWARD,      // ADB 端口转发
    PUSH_SERVER,      // 推送 scrcpy-server
    START_SERVER,     // 启动 scrcpy-server
    CONNECT_SOCKET,   // 连接 Socket
    COMPLETED         // 完成
}

/**
 * 步骤状态
 */
enum class StepStatus {
    PENDING,   // 等待中
    RUNNING,   // 执行中
    SUCCESS,   // 成功
    FAILED     // 失败
}

/**
 * 连接进度信息
 */
data class ConnectionProgress(
    val step: ConnectionStep,
    val status: StepStatus,
    val message: String = "",
    val error: String? = null
)

/**
 * 获取步骤的显示文本
 */
fun ConnectionStep.getDisplayText(): String {
    return when (this) {
        ConnectionStep.ADB_CONNECT -> "ADB Connect"
        ConnectionStep.ADB_FORWARD -> "ADB Forward"
        ConnectionStep.PUSH_SERVER -> "Push Server"
        ConnectionStep.START_SERVER -> "Start Server"
        ConnectionStep.CONNECT_SOCKET -> "Connect Socket"
        ConnectionStep.COMPLETED -> "Completed"
    }
}

/**
 * 获取步骤的图标
 */
fun StepStatus.getIcon(): String {
    return when (this) {
        StepStatus.PENDING -> "⏳"
        StepStatus.RUNNING -> "🔄"
        StepStatus.SUCCESS -> "✅"
        StepStatus.FAILED -> "❌"
    }
}

// ============ 分组管理 ============

/**
 * 分组类型
 */
enum class GroupType {
    SESSION,      // 会话分组
    AUTOMATION    // 自动化分组
}

/**
 * 设备分组
 */
data class DeviceGroup(
    val id: String,
    val name: String,
    val type: GroupType = GroupType.SESSION,  // 分组类型
    val path: String = "/",              // 完整路径，如 "/FRP/HZ"
    val parentPath: String = "/",        // 父路径，如 "/FRP"
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取路径深度（层级）
     */
    fun getDepth(): Int {
        return if (path == "/") 0 else path.count { it == '/' }
    }
    
    /**
     * 检查是否为根分组
     */
    fun isRoot(): Boolean = path == "/"
    
    /**
     * 检查是否为某个路径的子分组
     */
    fun isChildOf(parentPath: String): Boolean {
        return this.parentPath == parentPath
    }
    
    /**
     * 检查是否为某个路径的后代分组（包括子、孙等）
     */
    fun isDescendantOf(ancestorPath: String): Boolean {
        return path.startsWith("$ancestorPath/")
    }
}

/**
 * 树形节点（用于 UI 展示）
 */
data class GroupTreeNode(
    val group: DeviceGroup,
    val children: List<GroupTreeNode> = emptyList(),
    val isExpanded: Boolean = false,
    val level: Int = 0  // 层级深度，用于缩进
)

/**
 * 默认分组
 */
object DefaultGroups {
    const val ALL_DEVICES = "all_devices"
    const val UNGROUPED = "ungrouped"
}
