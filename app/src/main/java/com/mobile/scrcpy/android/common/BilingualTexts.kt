package com.mobile.scrcpy.android.common

/**
 * 双语文本定义
 * 每个文本包含中文和英文两个版本
 */
object BilingualTexts {
    // 设置页面
    val SETTINGS_TITLE = TextPair("设置", "Settings")
    val SETTINGS_GENERAL = TextPair("通用", "General")
    val SETTINGS_ADB_MANAGEMENT = TextPair("ADB 管理", "ADB Management")
    val SETTINGS_APP_LOGS = TextPair("应用日志", "App Logs")
    val SETTINGS_FEEDBACK_SUPPORT = TextPair("反馈与支持", "Feedback & Support")
    val SETTINGS_APPEARANCE = TextPair("外观", "Appearance")
    val SETTINGS_LANGUAGE = TextPair("语言 / Language", "Language / 语言")
    val SETTINGS_ABOUT = TextPair("关于 Scrcpy Remote", "About Scrcpy Remote")
    val SETTINGS_KEEP_ALIVE = TextPair("后台保持活跃", "Keep Alive in Background")
    val SETTINGS_SHOW_ON_LOCK_SCREEN = TextPair("在灵动岛显示实况", "Show on Dynamic Island")
    val SETTINGS_FLOATING_HAPTIC = TextPair("悬浮球触感反馈", "Floating Ball Haptic Feedback")
    val SETTINGS_MANAGE_ADB_KEYS = TextPair("管理 ADB 密钥", "Manage ADB Keys")
    val SETTINGS_ADB_PAIRING = TextPair("使用配对码进行 ADB 配对", "ADB Pairing with Code")
    val SETTINGS_FILE_TRANSFER_PATH = TextPair("文件发送默认路径", "Default File Transfer Path")
    val SETTINGS_ENABLE_LOG = TextPair("启用日志记录", "Enable Logging")
    val SETTINGS_LOG_MANAGEMENT = TextPair("日志管理", "Log Management")
    val SETTINGS_CLEAR_LOGS = TextPair("清除全部日志", "Clear All Logs")
    val SETTINGS_SUBMIT_ISSUE = TextPair("提交问题", "Submit Issue")
    val SETTINGS_USER_GUIDE = TextPair("使用指南", "User Guide")

    // ADB 密钥管理
    val ADB_KEY_MANAGEMENT_TITLE = TextPair("管理 ADB 密钥", "Manage ADB Keys")
    val ADB_KEY_DIR_LABEL = TextPair("密钥目录", "Keys Directory")
    val ADB_PRIVATE_KEY_LABEL = TextPair("私钥 (ADBKEY)", "Private Key (ADBKEY)")
    val ADB_PUBLIC_KEY_LABEL = TextPair("公钥 (ADBKEY.PUB)", "Public Key (ADBKEY.PUB)")
    val ADB_KEY_NOT_FOUND = TextPair("未找到密钥", "Keys not found")
    val ADB_KEY_SAVE_SUCCESS = TextPair("密钥保存成功", "Keys saved successfully")
    val ADB_KEY_SAVE_FAILED = TextPair("密钥保存失败", "Failed to save keys")
    val ADB_KEY_IMPORT_SUCCESS = TextPair("密钥导入成功", "Keys imported successfully")
    val ADB_KEY_IMPORT_FAILED = TextPair("密钥导入失败", "Failed to import keys")
    val ADB_KEY_EXPORT_SUCCESS = TextPair("密钥已导出到", "Keys exported to")
    val ADB_KEY_EXPORT_FAILED = TextPair("密钥导出失败", "Failed to export keys")
    val ADB_KEY_GENERATE_SUCCESS = TextPair("新密钥对生成成功", "New key pair generated successfully")
    val ADB_KEY_GENERATE_FAILED = TextPair("密钥生成失败", "Failed to generate keys")
    val ADB_KEY_GENERATE_CONFIRM_TITLE = TextPair("生成新的 ADB 密钥对", "Generate New ADB Key Pair")
    val ADB_KEY_DESTRUCTIVE_OP = TextPair("这是一个破坏性操作！", "This is a destructive operation!")
    val ADB_KEY_CURRENT_KEYS_DELETED = TextPair("你当前的 ADB 密钥将被永久删除", "Your current ADB keys will be permanently deleted")
    val ADB_KEY_DEVICES_LOSE_AUTH = TextPair("之前使用当前密钥授权的所有设备将失去授权", "All devices previously authorized with current keys will lose authorization")
    val ADB_KEY_NEED_REAUTH = TextPair("你需要手动重新授权所有设备", "You need to manually re-authorize all devices")
    val ADB_KEY_CANNOT_UNDO = TextPair("此操作无法撤销", "This operation cannot be undone")
    val ADB_KEY_CONFIRM_GENERATE = TextPair("确定要生成新的 ADB 密钥吗？", "Are you sure you want to generate new ADB keys?")
    val BUTTON_GENERATE_KEYS = TextPair("生成新密钥对", "Generate New Keys")
    val BUTTON_IMPORT_KEYS = TextPair("导入密钥", "Import Keys")
    val BUTTON_EXPORT_KEYS = TextPair("导出密钥", "Export Keys")
    val BUTTON_SAVE_KEYS = TextPair("保存密钥", "Save Keys")
    val BUTTON_CONFIRM = TextPair("确定", "Confirm")
    val BUTTON_HIDE = TextPair("隐藏", "Hide")
    val BUTTON_SHOW = TextPair("显示", "Show")
    val BUTTON_CLOSE = TextPair("关闭", "Close")
    
    // 日志管理
    val LOG_MANAGEMENT_TITLE = TextPair("日志管理", "Log Management")
    val LOG_SEARCH_PLACEHOLDER = TextPair("搜索日志内容...", "Search logs...")
    val LOG_FILTER_BY_TAG = TextPair("按标签筛选", "Filter by Tag")
    val LOG_ALL_TAGS = TextPair("全部标签", "All Tags")
    val LOG_SHARE_BUTTON = TextPair("分享", "Share")
    val LOG_FILE_TOO_LARGE_TITLE = TextPair("文件过大", "File Too Large")
    val LOG_FILE_TOO_LARGE_MESSAGE = TextPair(
        "日志文件超过 1MB，无法直接查看。\n\n建议先清理旧日志，然后重现问题以生成新的日志文件。",
        "Log file exceeds 1MB and cannot be viewed directly.\n\nPlease clear old logs first, then reproduce the issue to generate a new log file."
    )
    val LOG_CLEAR_AND_RETRY = TextPair("清理日志", "Clear Logs")
    val LOG_NO_RESULTS = TextPair("未找到匹配的日志", "No matching logs found")
    val LOG_FILE_LABEL = TextPair("文件", "File")
    val LOG_SIZE_LABEL = TextPair("大小", "Size")
    val LOG_MODIFIED_LABEL = TextPair("最后修改", "Modified")
    val LOG_DELETE_CONFIRM_TITLE = TextPair("删除日志文件", "Delete Log File")
    val LOG_DELETE_CONFIRM_MESSAGE = TextPair("确定要删除 %s 吗？", "Are you sure you want to delete %s?")
    val LOG_DELETE_BUTTON = TextPair("删除", "Delete")
    val LOG_REFRESH_BUTTON = TextPair("刷新", "Refresh")
    val LOG_STATS_TITLE = TextPair("日志文件统计", "Log Statistics")
    val LOG_FILE_COUNT = TextPair("文件总数", "File Count")
    val LOG_TOTAL_SIZE = TextPair("总大小", "Total Size")
    val LOG_CURRENT_SIZE = TextPair("当前日志大小", "Current Log Size")
    val LOG_QUICK_ACTIONS = TextPair("快捷自动化", "Quick Actions")
    val LOG_CLEAR_OLD_LOGS = TextPair("清除旧日志", "Clear Old Logs")
    val LOG_KEEP_CURRENT_ONLY = TextPair("仅保留当前", "Keep Current Only")
    val LOG_FILES_SECTION = TextPair("日志文件", "Log Files")
    val LOG_VIEW_BUTTON = TextPair("View", "View")
    val LOG_CURRENT_BUTTON = TextPair("当前", "Current")
    
    // LogManager 内部日志
    val LOG_SYSTEM_INIT_SUCCESS = TextPair("日志系统初始化完成", "Log system initialized")
    val LOG_INIT_FILE_FAILED = TextPair("初始化日志文件失败", "Failed to initialize log file")
    val LOG_CLOSE_FILE_FAILED = TextPair("关闭日志文件失败", "Failed to close log file")
    val LOG_WRITE_FAILED = TextPair("写入日志失败", "Failed to write log")
    val LOG_DELETE_FILE_FAILED = TextPair("删除日志文件失败", "Failed to delete log file")
    val LOG_READ_FILE_FAILED = TextPair("读取日志文件失败", "Failed to read log file")
    val LOG_READ_FILE_ERROR = TextPair("读取日志文件失败", "Failed to read log file")
    val LOG_WRITE_RAW_FAILED = TextPair("写入原始日志失败", "Failed to write raw log")
    
    // 编解码器测试
    val CODEC_TEST_TITLE = TextPair("测试音频编解码器", "Test Audio Codecs")
    val CODEC_TEST_BUTTON = TextPair("点击测试", "Tap to Test")
    val CODEC_TEST_SEARCH_PLACEHOLDER = TextPair("搜索编解码器", "Search codec")
    val CODEC_TEST_FOUND_COUNT = TextPair("共找到", "Found")
    val CODEC_TEST_AUDIO_CODECS = TextPair("个音频编解码器", "audio codecs")
    val CODEC_TEST_WARNING_OPUS = TextPair("⚠️ 注意：部分设备的 Opus 解码器可能不兼容裸 Opus 帧，建议使用 AAC", "⚠️ Note: Some devices' Opus decoders may not support raw Opus frames, AAC is recommended")
    val CODEC_TEST_INFO_COMPATIBILITY = TextPair("💡 说明：测试功能未适配所有解码格式，如果测试没有声音，可能是适配问题", "💡 Info: Test function may not support all formats, no sound may indicate compatibility issues")
    val CODEC_TEST_TYPE_LABEL = TextPair("类型", "Type")
    val CODEC_TEST_ENCODER = TextPair("编码器", "Encoder")
    val CODEC_TEST_DECODER = TextPair("解码器", "Decoder")
    val CODEC_TEST_FILTER_ALL = TextPair("全部", "All")
    val CODEC_TEST_SAMPLE_RATE = TextPair("采样率", "Sample Rate")
    val CODEC_TEST_MAX_CHANNELS = TextPair("最大声道", "Max Channels")
    val CODEC_TEST_ACTUAL = TextPair("实际", "Actual")
    val CODEC_TEST_NO_DETAILS = TextPair("无法获取详细信息", "Unable to get details")
    
    // 主页面
    val MAIN_TITLE_SESSIONS = TextPair("Scrcpy Sessions", "Scrcpy Sessions")
    val MAIN_TAB_SESSIONS = TextPair("会话", "Sessions")
    val MAIN_TAB_ACTIONS = TextPair("自动化", "Actions")
    val MAIN_ADD_SESSION = TextPair("添加会话", "Add Session")
    val MAIN_ADD_ACTION = TextPair("添加自动化", "Add Action")
    
    // 会话列表
    val SESSION_NO_SESSIONS = TextPair("没有 Scrcpy Sessions", "No Scrcpy Sessions")
    val SESSION_CLICK_TO_CONNECT = TextPair("点击连接", "Tap to Connect")
    val SESSION_CONNECTED = TextPair("已连接", "Connected")
    val SESSION_CONFIRM_DELETE = TextPair("确认删除", "Confirm Delete")
    val SESSION_DELETE = TextPair("删除", "Delete")
    val SESSION_CANCEL = TextPair("取消", "Cancel")
    val SESSION_URL_COPIED = TextPair("URL 已复制", "URL Copied")
    val SESSION_EDIT = TextPair("编辑会话", "Edit Session")
    val SESSION_DELETE_SESSION = TextPair("删除会话", "Delete Session")
    val SESSION_CONNECT = TextPair("连接会话", "Connect Session")
    val SESSION_COPY = TextPair("复制会话", "Copy Session")
    val SESSION_EMPTY_HINT = TextPair(
        "点击右上角 + 按钮开始新的 scrcpy 会话。\n会话会保存在此处以便快速访问。",
        "Tap the + button in the top right to start a new scrcpy session.\nSessions will be saved here for quick access."
    )
    val SESSION_SAVE_BUTTON = TextPair("保存会话", "Save Session")

    val GROUP_ALL = TextPair("主页", "Home")
    val GROUP_UNGROUPED = TextPair("未分组", "Ungrouped")
    val GROUP_MANAGE = TextPair("管理分组", "Manage Groups")
    val GROUP_ADD = TextPair("添加分组", "Add Group")
    val GROUP_EDIT = TextPair("编辑分组", "Edit Group")
    val GROUP_DELETE = TextPair("删除分组", "Delete Group")
    val GROUP_NAME = TextPair("分组名称", "Group Name")
    val GROUP_DESCRIPTION = TextPair("分组描述", "Group Description")
    val GROUP_SELECT = TextPair("选择分组", "Select Groups")
    val GROUP_CONFIRM_DELETE = TextPair("确认删除分组", "Confirm Delete Group")
    val GROUP_CONFIRM_DELETE_MESSAGE = TextPair("确定要删除分组 \"%s\" 吗？", "Are you sure you want to delete group \"%s\"?")
    val GROUP_PLACEHOLDER_NAME = TextPair("输入分组名称", "Enter group name")
    val GROUP_PLACEHOLDER_DESCRIPTION = TextPair("可选", "Optional")
    val GROUP_PARENT_PATH = TextPair("父路径", "Parent Path")
    val GROUP_PATH_PREVIEW = TextPair("完整路径预览", "Full Path Preview")
    val GROUP_SELECT_PATH = TextPair("选择路径", "Select Path")
    val GROUP_ROOT = TextPair("首页", "Home")
    val GROUP_TYPE = TextPair("分组类型", "Group Type")

    // 自动化页面
    val ACTIONS_NO_ACTIONS = TextPair("没有自动化", "No Actions")
    val ACTIONS_EMPTY_HINT = TextPair(
        "点击右上角 + 按钮创建新的 Scrcpy Action。\nAction 用于启动 Scrcpy 会话并自动执行自定义动作。",
        "Tap the + button in the top right to create a new Scrcpy Action.\nActions are used to start Scrcpy sessions and automatically execute custom operations."
    )
    
    // 对话框标题
    val DIALOG_CREATE_SESSION = TextPair("创建会话", "Create Session")
    val DIALOG_EDIT_SESSION = TextPair("编辑会话", "Edit Session")
    val DIALOG_SELECT_VIDEO_ENCODER = TextPair("选择视频编码器", "Select Video Encoder")
    val DIALOG_SELECT_AUDIO_ENCODER = TextPair("选择音频编码器", "Select Audio Encoder")
    
    // 按钮
    val BUTTON_DONE = TextPair("完成", "Done")
    val BUTTON_CANCEL = TextPair("取消", "Cancel")
    val BUTTON_SAVE = TextPair("保存", "Save")
    val BUTTON_ADD = TextPair("添加", "Add")
    
    // 会话对话框 - 分组标题
    val SECTION_REMOTE_DEVICE = TextPair("远程设备", "Remote Device")
    val SECTION_CONNECTION_OPTIONS = TextPair("连接选项", "Connection Options")
    val SECTION_ADB_SESSION_OPTIONS = TextPair("ADB 会话选项", "ADB Session Options")
    val SECTION_ENCODER_OPTIONS = TextPair("编码器选项", "Encoder Options")
    val SECTION_DETECTED_ENCODERS = TextPair("检测到的编码器", "Detected Encoders")
    val SECTION_DETECTED_AUDIO_ENCODERS = TextPair("检测到的音频编码器", "Detected Audio Encoders")
    
    // 会话对话框 - 标签
    val LABEL_SESSION_NAME = TextPair("会话名称", "Session Name")
    val LABEL_HOST = TextPair("主机", "Host")
    val LABEL_PORT = TextPair("端口", "Port")
    val LABEL_MAX_SIZE = TextPair("最大尺寸", "Max Size")
    val LABEL_BITRATE = TextPair("码率", "Bitrate")
    val LABEL_MAX_FPS = TextPair("最大帧率", "Max FPS")
    val LABEL_VIDEO_CODEC = TextPair("视频编码", "Video Codec")
    val LABEL_VIDEO_ENCODER = TextPair("视频编码器", "Video Encoder")
    val LABEL_AUDIO_CODEC = TextPair("音频编码", "Audio Codec")
    val LABEL_AUDIO_ENCODER = TextPair("音频编码器", "Audio Encoder")
    val LABEL_AUDIO_VOLUME = TextPair("音量缩放", "Audio Volume")
    val LABEL_DEFAULT_ENCODER = TextPair("默认编码器", "Default Encoder")
    val LABEL_TEST_AUDIO_DECODER = TextPair("测试音频解码器", "Test Audio Decoder")
    val LABEL_DEVICE_INFO = TextPair("设备信息", "Device Info")
    val LABEL_DEVICE_ID = TextPair("设备 ID", "Device ID")
    val LABEL_DEVICE = TextPair("设备", "Device")
    val LABEL_INTERVAL = TextPair("间隔", "Interval")
    val LABEL_USING = TextPair("使用", "Using")
    val LABEL_EXECUTE_COMMAND = TextPair("执行命令", "Execute command")
    val LABEL_RECEIVED_OUTPUT = TextPair("收到输出", "Received output")
    val LABEL_CHARACTERS = TextPair("字符", "characters")
    val LABEL_ITEMS = TextPair("个", "items")
    val LABEL_KEY_INFO = TextPair("密钥信息", "Key Information")
    val LABEL_KEY_OPERATIONS = TextPair("密钥操作", "Key Operations")
    val LABEL_STATUS = TextPair("状态", "Status")
    val ERROR_LABEL = TextPair("错误", "Error")
    
    // 会话对话框 - 开关
    val SWITCH_FORCE_ADB = TextPair("强制使用 ADB 转发连接", "Force ADB Forward")
    val SWITCH_ENABLE_AUDIO = TextPair("启用音频", "Enable Audio")
    val SWITCH_STAY_AWAKE = TextPair("保持唤醒", "Stay Awake")
    val SWITCH_TURN_SCREEN_OFF = TextPair("连接后关闭远程屏幕", "Turn Screen Off")
    val SWITCH_POWER_OFF_ON_CLOSE = TextPair("断开后锁定远程屏幕(按电源键)", "Power Off on Close")
    val SWITCH_KEEP_DEVICE_AWAKE = TextPair("使用期间保持设备唤醒", "Keep Device Awake")
    val SWITCH_ENABLE_HARDWARE_DECODING = TextPair("启用硬件解码", "Enable Hardware Decoding")
    val SWITCH_FOLLOW_ORIENTATION = TextPair("跟随设备旋转变化", "Follow Remote Orientation Change")
    val SWITCH_NEW_DISPLAY = TextPair("启动新的显示", "New Display")
    
    // 会话对话框 - 提示
    val HINT_EMPTY_USE_DEVICE_RESOLUTION = TextPair("留空使用设备分辨率 示例: 720", "Empty for device resolution Example: 720")
    
    // 会话对话框 - 状态
    val STATUS_DETECTING_VIDEO_ENCODERS = TextPair("正在检测视频编码器...", "Detecting video encoders...")
    val STATUS_DETECTING_AUDIO_ENCODERS = TextPair("正在检测音频编码器...", "Detecting audio encoders...")
    val STATUS_DETECTION_FAILED = TextPair("检测失败", "Detection failed")
    val STATUS_NO_ENCODERS_DETECTED = TextPair("未检测到编码器", "No encoders detected")
    val STATUS_NO_AUDIO_ENCODERS_DETECTED = TextPair("未检测到音频编码器", "No audio encoders detected")
    val STATUS_CONNECTING = TextPair("正在连接...", "Connecting...")
    val ERROR_CONNECTION_FAILED = TextPair("连接失败", "Connection failed")
    val ERROR_CANNOT_GET_CONNECTION = TextPair("无法获取设备连接", "Cannot get device connection")
    val ERROR_DETECTION_EXCEPTION = TextPair("检测异常", "Detection exception")
    val ERROR_DETECTION_FAILED = TextPair("检测失败", "Detection failed")
    
    // ADB 连接错误
    val ERROR_ADB_CONNECTION_DISCONNECTED = TextPair("ADB 连接已断开 (ECONNREFUSED)", "ADB connection disconnected (ECONNREFUSED)")
    val ERROR_ADB_HANDSHAKE_FAILED = TextPair("ADB 握手失败，设备可能未授权或 ADB 服务异常", "ADB handshake failed, device may be unauthorized or ADB service error")
    val ERROR_ADB_CONNECTION_UNAVAILABLE = TextPair("ADB 连接不可用", "ADB connection unavailable")
    val ERROR_ADB_COMMAND_FAILED = TextPair("ADB 命令执行失败", "ADB command execution failed")
    
    // 连接失败页面
    val CONNECTION_FAILED_TITLE = TextPair("连接失败", "Connection Failed")
    val BUTTON_RECONNECT = TextPair("重新连接", "Reconnect")
    val BUTTON_CANCEL_CONNECTION = TextPair("取消连接", "Cancel")
    
    // Scrcpy 连接进度
    val PROGRESS_VERIFYING_ADB = TextPair("验证 ADB 连接", "Verifying ADB connection")
    val PROGRESS_ADB_RECONNECTING = TextPair("ADB 连接已断开，重新连接...", "ADB disconnected, reconnecting...")
    val PROGRESS_ADB_NORMAL = TextPair("ADB 连接正常", "ADB connection normal")
    val PROGRESS_PORT_FORWARD = TextPair("端口转发已建立", "Port forwarding established")
    val PROGRESS_SERVER_PUSHED = TextPair("服务端已推送", "Server pushed")
    val PROGRESS_SERVER_STARTED = TextPair("服务端已启动", "Server started")
    val PROGRESS_SOCKET_CONNECTED = TextPair("Socket 连接成功", "Socket connected")
    val PROGRESS_CONNECTION_ESTABLISHED = TextPair("连接已建立", "Connection established")
    val PROGRESS_PUSHING_SERVER = TextPair("推送 scrcpy-server", "Pushing scrcpy-server")
    val PROGRESS_STARTING_SERVER = TextPair("启动 scrcpy-server", "Starting scrcpy-server")
    val PROGRESS_CONNECTING_STREAM = TextPair("连接视频流", "Connecting video stream")
    
    // Scrcpy 错误消息
    val ERROR_ADB_RECONNECT_FAILED = TextPair("ADB 重连失败", "ADB reconnection failed")
    val ERROR_INVALID_DEVICE_ID = TextPair("无效的设备 ID", "Invalid device ID")
    val ERROR_CANNOT_GET_ADB_CONNECTION = TextPair("无法获取 ADB 连接", "Cannot get ADB connection")
    val ERROR_DEVICE_NOT_CONNECTED = TextPair("未连接设备", "Device not connected")
    val ERROR_DEVICE_CONNECTION_LOST = TextPair("设备连接已断开", "Device connection lost")
    val ERROR_CONTROL_NOT_READY = TextPair("控制连接未就绪", "Control connection not ready")
    val ERROR_SEND_FAILED = TextPair("发送失败", "Send failed")
    val ERROR_TEXT_TOO_LONG = TextPair("文本过长（最大 300 字节）", "Text too long (max 300 bytes)")
    
    // 会话对话框 - 占位符
    val PLACEHOLDER_CUSTOM_ENCODER = TextPair("自定义编码器名称", "Custom encoder name")
    val PLACEHOLDER_SEARCH_ENCODER = TextPair("搜索编码器...", "Search encoder...")
    
    // 编码器选择对话框
    val ENCODER_FILTER_ALL = TextPair("全部", "All")
    val ENCODER_REFRESH_BUTTON = TextPair("刷新编码器", "Refresh Encoders")
    val ENCODER_ERROR_INPUT_HOST = TextPair("请先输入主机地址", "Please enter host first")
    
    // 占位符文本（输入框提示）
    val PLACEHOLDER_SESSION_NAME = TextPair("可选", "Optional")
    val PLACEHOLDER_BITRATE = TextPair("16M、4M 或 720K", "16M、4M or 720K")
    val PLACEHOLDER_MAX_FPS = TextPair("默认 30 示例：15", "Default 30 Example：15")
    val PLACEHOLDER_DEFAULT_ENCODER = TextPair("默认编码器", "Default Encoder")
    val PLACEHOLDER_DEFAULT_AUDIO_ENCODER = TextPair("默认", "Default")
    
    // 时间单位
    val TIME_1_MINUTE = TextPair("1 分钟", "1 minute")
    val TIME_5_MINUTES = TextPair("5 分钟", "5 minutes")
    val TIME_10_MINUTES = TextPair("10 分钟", "10 minutes")
    val TIME_30_MINUTES = TextPair("30 分钟", "30 minutes")
    val TIME_1_HOUR = TextPair("1 小时", "1 hour")
    val TIME_ALWAYS = TextPair("始终", "Always")
    
    // ADB 连接管理器
    val ADB_MANAGER_INIT = TextPair("ADB 连接管理器初始化", "ADB connection manager initialized")
    val ADB_HEARTBEAT_FAILED = TextPair("心跳检测失败", "Heartbeat check failed")
    val ADB_CONNECTION_DETECTED_DISCONNECTED = TextPair("检测到 ADB 连接断开", "ADB connection disconnected detected")
    val ADB_CLEANUP_INVALID_CONNECTION = TextPair("清理失效连接", "Cleaning up invalid connection")
    val ADB_KEEPALIVE_STARTED = TextPair("连接保活任务已启动", "Connection keep-alive task started")
    val ADB_GENERATE_NEW_KEYPAIR = TextPair("生成新的 ADB 密钥对", "Generating new ADB key pair")
    val ADB_KEYPAIR_LOADED = TextPair("ADB 密钥对加载成功", "ADB key pair loaded successfully")
    val ADB_KEYPAIR_INIT_FAILED = TextPair("初始化密钥对失败", "Failed to initialize key pair")
    val ADB_START_CONNECTING = TextPair("开始连接设备", "Starting device connection")
    val ADB_TARGET_ADDRESS = TextPair("目标地址", "Target address")
    val ADB_KEYPAIR_NOT_INITIALIZED = TextPair("ADB 密钥对未初始化", "ADB key pair not initialized")
    val ADB_FORCE_RECONNECT_CLEANUP = TextPair("强制重连，清理旧连接", "Force reconnect, cleaning up old connection")
    val ADB_VERIFYING_CONNECTION = TextPair("发现已存在的连接，验证可用性...", "Found existing connection, verifying availability...")
    val ADB_CONNECTION_VERIFIED = TextPair("连接验证成功，复用", "Connection verified, reusing")
    val ADB_CONNECTION_VERIFY_FAILED = TextPair("连接验证失败，清理后重连", "Connection verification failed, cleaning up and reconnecting")
    val ADB_CREATING_NEW_CONNECTION = TextPair("创建新的 ADB 连接...", "Creating new ADB connection...")
    val ADB_CONNECTION_REFUSED = TextPair("连接被拒绝", "Connection refused")
    val ADB_CONNECTION_REFUSED_DETAILS = TextPair(
        "连接被拒绝，请检查：\n1. 设备 IP 地址是否正确\n2. 设备是否开启 ADB 网络调试\n3. 端口号是否正确（默认 5555）",
        "Connection refused, please check:\n1. Device IP address is correct\n2. ADB network debugging is enabled on device\n3. Port number is correct (default 5555)"
    )
    val ADB_DADB_CREATED = TextPair("Dadb 实例创建成功", "Dadb instance created successfully")
    val ADB_ADDED_TO_POOL = TextPair("连接已加入连接池", "Connection added to pool")
    val ADB_GET_DEVICE_INFO_FAILED = TextPair("获取完整设备信息失败", "Failed to get complete device info")
    val ADB_CONNECTION_SUCCESS = TextPair("设备连接成功", "Device connected successfully")
    val ADB_CONNECTION_FAILED_TITLE = TextPair("连接设备失败", "Failed to connect device")
    val ADB_VERIFYING = TextPair("验证 ADB 连接...", "Verifying ADB connection...")
    val ADB_VERIFY_SUCCESS = TextPair("ADB 连接验证成功", "ADB connection verified successfully")
    val ADB_VERIFY_FAILED = TextPair("ADB 连接验证失败", "ADB connection verification failed")
    val ADB_CLOSE_DADB_ERROR = TextPair("关闭 dadb 时出错", "Error closing dadb")
    val ADB_DISCONNECTED_ECONNREFUSED = TextPair("ADB 连接已断开 (ECONNREFUSED)", "ADB connection disconnected (ECONNREFUSED)")
    val ADB_RECONNECT_DEVICE = TextPair("ADB 连接已断开，请重新连接设备", "ADB connection disconnected, please reconnect device")
    val ADB_HANDSHAKE_FAILED_OR_INTERRUPTED = TextPair("ADB 握手失败或连接中断", "ADB handshake failed or connection interrupted")
    val ADB_COMMUNICATION_FAILED = TextPair("ADB 通信失败，连接不可用", "ADB communication failed, connection unavailable")
    val ADB_GET_DEVICE_INFO_FAILED_DETAIL = TextPair("获取设备信息失败", "Failed to get device info")
    val ADB_CANNOT_GET_DEVICE_INFO = TextPair("无法获取设备信息", "Cannot get device info")
    val ADB_DEVICE_DISCONNECTED = TextPair("设备已断开", "Device disconnected")
    val ADB_DEVICE_NOT_CONNECTED = TextPair("设备未连接", "Device not connected")
    val ADB_DISCONNECT_FAILED = TextPair("断开设备失败", "Failed to disconnect device")
    val ADB_DISCONNECT_ALL = TextPair("断开所有设备连接", "Disconnecting all devices")
    val ADB_CLOSE_CONNECTION_FAILED = TextPair("关闭连接失败", "Failed to close connection")
    val ADB_GET_PUBLIC_KEY_FAILED = TextPair("获取公钥失败", "Failed to get public key")
    val ADB_CANNOT_EXECUTE_COMMAND = TextPair("无法执行命令", "Cannot execute command")
    val ADB_AUTO_RECONNECT_RETRY = TextPair("ADB 连接已关闭，尝试自动重连后重试", "ADB connection closed, retrying after auto-reconnect")
    val ADB_AUTO_RECONNECT_SUCCESS = TextPair("自动重连成功，命令执行成功", "Auto-reconnect successful, command executed")
    val ADB_AUTO_RECONNECT_STILL_FAILED = TextPair("自动重连后仍失败", "Still failed after auto-reconnect")
    val ADB_SOCKET_EXCEPTION_RETRY = TextPair("ADB Socket 异常，尝试自动重连后重试", "ADB Socket exception, retrying after auto-reconnect")
    val ADB_SOCKET_EXCEPTION = TextPair("ADB Socket 异常，无法执行命令", "ADB Socket exception, cannot execute command")
    val ADB_EXECUTE_COMMAND_FAILED = TextPair("执行命令失败", "Failed to execute command")
    val ADB_ASYNC_EXECUTE_FAILED = TextPair("异步执行命令失败", "Failed to execute command asynchronously")
    val ADB_OPEN_SHELL_STREAM_FAILED = TextPair("打开 Shell 流失败", "Failed to open shell stream")
    val ADB_PORT_FORWARD_SUCCESS = TextPair("端口转发设置成功", "Port forwarding set up successfully")
    val ADB_PORT_FORWARD_FAILED = TextPair("端口转发失败", "Port forwarding failed")
    val ADB_FORWARD_SETUP_SUCCESS = TextPair("ADB forward 设置成功", "ADB forward set up successfully")
    val ADB_SOCKET_FORWARDER_FAILED = TextPair("SocketForwarder 失败", "SocketForwarder failed")
    val ADB_FORWARD_SETUP_EXCEPTION = TextPair("设置 ADB forward 异常", "Exception setting up ADB forward")
    val ADB_FORWARD_REMOVED = TextPair("ADB forward 已移除", "ADB forward removed")
    val ADB_FORWARD_REMOVE_EXCEPTION = TextPair("移除 ADB forward 异常", "Exception removing ADB forward")
    val ADB_FILE_PUSH_SUCCESS = TextPair("文件推送成功", "File pushed successfully")
    val ADB_FILE_PUSH_FAILED = TextPair("文件推送失败", "Failed to push file")
    val ADB_FILE_PULL_SUCCESS = TextPair("文件拉取成功", "File pulled successfully")
    val ADB_FILE_PULL_FAILED = TextPair("文件拉取失败", "Failed to pull file")
    val ADB_APK_INSTALL_SUCCESS = TextPair("APK 安装成功", "APK installed successfully")
    val ADB_APK_INSTALL_FAILED = TextPair("APK 安装失败", "Failed to install APK")
    val ADB_APP_UNINSTALL_SUCCESS = TextPair("应用卸载成功", "App uninstalled successfully")
    val ADB_APP_UNINSTALL_FAILED = TextPair("应用卸载失败", "Failed to uninstall app")
    val ADB_SCRCPY_SERVER_NOT_IN_ASSETS = TextPair("scrcpy-server.jar 不存在于 assets 目录", "scrcpy-server.jar not found in assets directory")
    val ADB_PUSH_SCRCPY_SERVER_FAILED = TextPair("推送 scrcpy-server.jar 失败", "Failed to push scrcpy-server.jar")
    val ADB_DETECTING_VIDEO_ENCODERS = TextPair("开始检测视频编码器...", "Starting video encoder detection...")
    val ADB_PUSH_SERVER_FAILED_CANNOT_DETECT = TextPair("推送 scrcpy-server.jar 失败，无法检测编码器", "Failed to push scrcpy-server.jar, cannot detect encoders")
    val ADB_PUSH_FAILED = TextPair("推送失败", "Push failed")
    val ADB_CANNOT_OPEN_SHELL_STREAM = TextPair("无法打开 shell 流", "Cannot open shell stream")
    val ADB_SHELL_STREAM_EXIT = TextPair("Shell 流退出", "Shell stream exited")
    val ADB_READ_OUTPUT_ERROR = TextPair("读取输出时出错", "Error reading output")
    val ADB_DETECTED_VIDEO_ENCODERS = TextPair("检测到视频编码器", "Detected video encoders")
    val ADB_NO_ENCODERS_DETECTED_OUTPUT = TextPair("未检测到编码器，输出内容", "No encoders detected, output content")
    val ADB_DETECT_ENCODERS_FAILED = TextPair("检测编码器失败", "Failed to detect encoders")
    val ADB_DETECTING_AUDIO_ENCODERS = TextPair("开始检测音频编码器...", "Starting audio encoder detection...")
    val ADB_DETECTED_AUDIO_ENCODERS = TextPair("检测到音频编码器", "Detected audio encoders")
    val ADB_NO_AUDIO_ENCODERS_DETECTED_OUTPUT = TextPair("未检测到音频编码器，输出内容", "No audio encoders detected, output content")
    val ADB_DETECT_AUDIO_ENCODERS_FAILED = TextPair("检测音频编码器失败", "Failed to detect audio encoders")
    val ADB_CONNECTION_CLOSED = TextPair("连接已关闭", "Connection closed")
    val ADB_CLOSE_CONNECTION_FAILED_DETAIL = TextPair("关闭连接失败", "Failed to close connection")
    
    // 对话框
    val DIALOG_CLEAR_LOGS_TITLE = TextPair("清除全部日志", "Clear All Logs")
    val DIALOG_CLEAR_LOGS_MESSAGE = TextPair(
        "这将永久删除所有日志文件。此操作不可撤销！",
        "This will permanently delete all log files. This action cannot be undone!"
    )
    val DIALOG_CLEAR_LOGS_CONFIRM = TextPair("清除", "Clear")
    
    val DIALOG_FILE_PATH_TITLE = TextPair("文件发送路径", "File Transfer Path")
    val DIALOG_FILE_PATH_DEFAULT = TextPair("默认路径", "Default Path")
    val DIALOG_FILE_PATH_QUICK_SELECT = TextPair("快速选择", "Quick Select")
    val DIALOG_FILE_PATH_INFO = TextPair("信息", "Info")
    val DIALOG_FILE_PATH_INFO_TEXT = TextPair(
        "通过「发送文件」操作发送的文件将被推送到 Android 设备上的此路径。\n\n路径必须以 /sdcard/ 或类似可访问且开放的绝对路径。",
        "Files sent via 'Send File' will be pushed to this path on the Android device.\n\nThe path must be an accessible absolute path starting with /sdcard/ or similar."
    )
    val DIALOG_FILE_PATH_RESET = TextPair("重置为默认", "Reset to Default")
    
    // 语言设置
    val LANGUAGE_TITLE = TextPair("语言", "Language")
    val LANGUAGE_SECTION_TITLE = TextPair("语言 / Language", "Language / 语言")
    val LANGUAGE_AUTO = TextPair("跟随系统", "Follow System")
    val LANGUAGE_CHINESE = TextPair("中文", "中文")
    val LANGUAGE_ENGLISH = TextPair("English", "English")
    
    // 外观设置
    val APPEARANCE_TITLE = TextPair("外观", "Appearance")
    val THEME_SECTION_TITLE = TextPair("主题", "Theme")
    val THEME_SYSTEM = TextPair("跟随系统", "Follow System")
    val THEME_DARK = TextPair("深色模式", "Dark Mode")
    val THEME_LIGHT = TextPair("浅色模式", "Light Mode")

    val USB_SCANNING_DEVICES = TextPair("正在扫描 USB 设备...", "Scanning USB devices...")
    val USB_FOUND_DEVICES = TextPair("发现设备", "Found devices")
    val USB_DEVICE_FOUND = TextPair("发现 ADB 设备", "ADB device found")
    val USB_PERMISSION = TextPair("权限", "Permission")
    val USB_SCAN_FAILED = TextPair("扫描 USB 设备失败", "Failed to scan USB devices")
    val USB_PERMISSION_ALREADY_GRANTED = TextPair("USB 权限已授予", "USB permission already granted")
    val USB_REQUESTING_PERMISSION = TextPair("正在请求 USB 权限...", "Requesting USB permission...")
    val USB_PERMISSION_GRANTED = TextPair("USB 权限已授予", "USB permission granted")
    val USB_PERMISSION_DENIED = TextPair("USB 权限被拒绝", "USB permission denied")
    val USB_PERMISSION_REQUEST_FAILED = TextPair("请求 USB 权限失败", "Failed to request USB permission")
    val USB_CONNECTING_DEVICE = TextPair("正在连接 USB 设备", "Connecting USB device")
    val USB_DEVICE_QUERY = TextPair("设备查询", "Device query")
    val USB_CONNECT_FAILED = TextPair("USB 连接失败", "USB connection failed")
    val USB_NO_DEVICES_FOUND = TextPair("未找到 USB 设备", "No USB devices found")
    val USB_SCAN_BUTTON = TextPair("扫描 USB 设备", "Scan USB Devices")
    val USB_CONNECT_BUTTON = TextPair("连接", "Connect")
    val USB_DEVICE_LIST_TITLE = TextPair("USB 设备列表", "USB Device List")
    val USB_SERIAL_NUMBER = TextPair("序列号", "Serial Number")
    val USB_PERMISSION_GRANTED_STATUS = TextPair("已授权", "Granted")
    val USB_PERMISSION_NOT_GRANTED_STATUS = TextPair("未授权", "Not Granted")
    val USB_CLICK_TO_REQUEST_PERMISSION = TextPair("点击请求权限", "Click to request permission")
    
    // USB 错误
    val ERROR_USB_CONNECTION_LOST = TextPair("USB 连接已断开，请重新连接设备", "USB connection lost, please reconnect the device")
    
    // 连接类型
    val CONNECTION_TYPE = TextPair("连接类型", "Connection Type")
    val CONNECTION_TYPE_TCP = TextPair("TCP/IP", "TCP/IP")
    val CONNECTION_TYPE_USB = TextPair("USB", "USB")
    val USB_SELECT_DEVICE = TextPair("选择 USB 设备", "Select USB Device")
    val USB_DEVICE_SELECTED = TextPair("已选择设备", "Device Selected")
    val USB_NO_DEVICE_SELECTED = TextPair("未选择设备", "No Device Selected")
    
    val ADB_DEVICE_ALREADY_CONNECTED = TextPair("设备已连接", "Device already connected")
    
    // RemoteDisplayScreen & ScrcpyClient
    val REMOTE_SWITCH_TO_BACKGROUND = TextPair("切换到后台", "Switch to background")
    val REMOTE_SCREEN_ROTATION_A = TextPair("A旋转", "A rotation")
    val REMOTE_SCREEN_ROTATION_B = TextPair("B旋转", "B rotation")
    val REMOTE_LANDSCAPE = TextPair("横屏", "Landscape")
    val REMOTE_PORTRAIT = TextPair("竖屏", "Portrait")
    val REMOTE_ASPECT_RATIO = TextPair("宽高比", "Aspect ratio")
    val REMOTE_SCALE_STRATEGY = TextPair("缩放策略", "Scale strategy")
    val REMOTE_FILL_HEIGHT = TextPair("填满高度", "Fill height")
    val REMOTE_FILL_WIDTH = TextPair("填满宽度", "Fill width")
    val REMOTE_AUDIO_STREAM_EMPTY = TextPair("音频流为空，停止解码器", "Audio stream empty, stopping decoder")
    val REMOTE_AUDIO_STREAM_CHANGED = TextPair("音频流已变化，停止旧解码器", "Audio stream changed, stopping old decoder")
    val REMOTE_START_AUDIO_DECODER = TextPair("启动音频解码器", "Starting audio decoder")
    val REMOTE_AUDIO_CONNECTION_LOST = TextPair("音频连接丢失，触发完整清理", "Audio connection lost, triggering cleanup")
    val REMOTE_AUDIO_DECODER_CANCELLED = TextPair("音频解码器协程被取消", "Audio decoder coroutine cancelled")
    val REMOTE_AUDIO_DECODER_FAILED = TextPair("音频解码器失败", "Audio decoder failed")
    val REMOTE_INIT_AUDIO_DECODER_FAILED = TextPair("初始化音频解码器失败", "Failed to initialize audio decoder")
    val REMOTE_VIDEO_STREAM_CHANGED = TextPair("视频流已变化，重启解码器", "Video stream changed, restarting decoder")
    val REMOTE_PREPARE_VIDEO_DECODER = TextPair("准备启动视频解码器", "Preparing to start video decoder")
    val REMOTE_CANNOT_GET_VIDEO_RESOLUTION = TextPair("无法获取视频分辨率", "Cannot get video resolution")
    val REMOTE_VIDEO_RESOLUTION = TextPair("视频分辨率", "Video resolution")
    val REMOTE_RECEIVED_VIDEO_SIZE = TextPair("收到视频尺寸", "Received video size")
    val REMOTE_INVALID_VIDEO_SIZE = TextPair("无效的视频尺寸", "Invalid video size")
    val REMOTE_CACHED_VIDEO_DECODER = TextPair("已缓存视频解码器", "Cached video decoder")
    val REMOTE_SAVE_DECODER_CACHE_FAILED = TextPair("保存解码器缓存失败", "Failed to save decoder cache")
    val REMOTE_CONNECTION_LOST_CLEANUP = TextPair("连接丢失，触发完整清理", "Connection lost, triggering cleanup")
    val REMOTE_DECODER_CANCELLED_UI_CLOSED = TextPair("解码器已取消（界面关闭）", "Decoder cancelled (UI closed)")
    val REMOTE_DECODER_START_FAILED = TextPair("解码器启动失败", "Decoder start failed")
    val REMOTE_INIT_DECODER_FAILED = TextPair("初始化解码器失败", "Failed to initialize decoder")
    val REMOTE_DECODER_CONTINUE_RUNNING = TextPair("解码器继续运行，socket 保持活跃", "Decoder continues running, socket stays active")
    val REMOTE_RESUME_TO_FOREGROUND = TextPair("恢复到前台", "Resume to foreground")
    val REMOTE_FOREGROUND_RESUME_INVALID_SURFACE = TextPair("前台恢复但 Surface 无效", "Foreground resumed but Surface invalid")
    val REMOTE_START_CLEANUP_RESOURCES = TextPair("开始清理资源...", "Starting resource cleanup...")
    val REMOTE_CLEANUP_COMPLETE = TextPair("资源清理完成", "Resource cleanup complete")
    val REMOTE_CLEANUP_EXCEPTION = TextPair("资源清理异常", "Resource cleanup exception")
    val REMOTE_SURFACE_READY = TextPair("Surface 已就绪", "Surface ready")
    val REMOTE_SURFACE_DESTROYED = TextPair("Surface 已销毁", "Surface destroyed")
    val REMOTE_SURFACE_RESTORED = TextPair("Surface 已恢复，设置为就绪并恢复渲染", "Surface restored, set to ready and resume rendering")
    val REMOTE_SURFACE_UNAVAILABLE = TextPair("Surface 不可用", "Surface unavailable")
    val REMOTE_FOCUS_REQUEST_FAILED = TextPair("请求焦点失败", "Focus request failed")
    
    // ScrcpyClient
    val SCRCPY_NATIVE_LIB_LOAD_FAILED = TextPair("Native 库加载失败", "Native library load failed")
    val SCRCPY_ADB_CONNECTION_UNAVAILABLE = TextPair("ADB 连接不可用", "ADB connection unavailable")
    val SCRCPY_ADB_RECONNECT_SUCCESS = TextPair("ADB 重连成功", "ADB reconnection successful")
    val SCRCPY_CLEANED_OLD_SERVER_PROCESS = TextPair("已清理旧的 scrcpy-server 进程", "Cleaned old scrcpy-server process")
    val SCRCPY_CLEANUP_OLD_RESOURCES_FAILED = TextPair("清理旧资源失败", "Failed to cleanup old resources")
    val SCRCPY_PORT_FORWARD = TextPair("端口", "Port")
    val SCRCPY_CONNECTION_FAILED = TextPair("Scrcpy 连接失败", "Scrcpy connection failed")
    val SCRCPY_START_MONITOR_OUTPUT = TextPair("开始监控 scrcpy-server 输出", "Start monitoring scrcpy-server output")
    val SCRCPY_MONITOR_HEARTBEAT = TextPair("监控心跳", "Monitor heartbeat")
    val SCRCPY_TOTAL_LINES = TextPair("总行数", "Total lines")
    val SCRCPY_SINCE_LAST_OUTPUT = TextPair("距上次输出", "Since last output")
    val SCRCPY_NO_OUTPUT_FOR_SECONDS = TextPair("秒无输出", "seconds without output")
    val SCRCPY_NORMAL_EXIT = TextPair("正常退出", "Normal exit")
    val SCRCPY_ABNORMAL_EXIT = TextPair("异常退出", "Abnormal exit")
    val SCRCPY_EXITED = TextPair("已退出", "Exited")
    val SCRCPY_MONITOR_OUTPUT_END = TextPair("scrcpy-server 输出监控结束", "scrcpy-server output monitoring ended")
    val SCRCPY_WAIT_METADATA = TextPair("等待元数据...", "Waiting for metadata...")
    val SCRCPY_START_READ_METADATA = TextPair("开始读取元数据，可用字节", "Start reading metadata, available bytes")
    val SCRCPY_DEVICE_NAME = TextPair("设备名称", "Device name")
    val SCRCPY_CODEC_ID = TextPair("Codec ID", "Codec ID")
    val SCRCPY_RESOLUTION = TextPair("分辨率", "Resolution")
    val SCRCPY_VIDEO_PACKET = TextPair("视频包", "Video packet")
    val SCRCPY_SINCE_LAST_READ = TextPair("距上次读取", "Since last read")
    val SCRCPY_CONSECUTIVE_TIMEOUTS = TextPair("连续超时", "Consecutive timeouts")
    val SCRCPY_INVALID_PACKET_SIZE = TextPair("Invalid packet size", "Invalid packet size")
    val SCRCPY_PACKET_SIZE_ABNORMAL = TextPair("数据包大小异常", "Packet size abnormal")
    val SCRCPY_DATA_STREAM_OUT_OF_SYNC = TextPair("数据流不同步，可能需要重新连接", "Data stream out of sync, may need reconnection")
    val SCRCPY_VIDEO_STREAM_TIMEOUT = TextPair("视频流超时", "Video stream timeout")
    val SCRCPY_WAITED = TextPair("已等待", "Waited")
    val SCRCPY_TOTAL_PACKETS = TextPair("总包数", "Total packets")
    val SCRCPY_DEVICE_MAY_SLEEP = TextPair("设备可能息屏，继续等待视频流恢复（控制流正常）", "Device may be sleeping, continue waiting for video stream (control stream normal)")
    val SCRCPY_CONTROL_STREAM_DISCONNECTED = TextPair("控制流也断开，判定为连接断开", "Control stream also disconnected, determined as disconnected")
    val SCRCPY_CONNECTION_DISCONNECTED = TextPair("连接断开", "Connection disconnected")
    val SCRCPY_VIDEO_STREAM_CLOSED = TextPair("视频流已关闭", "Video stream closed")
    val SCRCPY_TOTAL_RECEIVED_PACKETS = TextPair("总共接收", "Total received")
    val SCRCPY_PACKETS = TextPair("个包", "packets")
    val SCRCPY_VIDEO_STREAM_READ_ERROR = TextPair("视频流读取错误", "Video stream read error")
    val SCRCPY_CLOSE_VIDEO_STREAM = TextPair("关闭视频流", "Close video stream")
    val SCRCPY_METADATA_READ_COMPLETE = TextPair("元数据读取完成", "Metadata read complete")
    val SCRCPY_METADATA_READ_FAILED = TextPair("元数据读取失败", "Metadata read failed")
    val SCRCPY_SCREEN_WAKE_SIGNAL_SENT = TextPair("屏幕唤醒信号已发送（已触发关键帧）", "Screen wake signal sent (key frame triggered)")
    val SCRCPY_WAKE_SCREEN_FAILED = TextPair("唤醒屏幕失败", "Failed to wake screen")
    val SCRCPY_CONNECTION_FAILED_DETAIL = TextPair("连接失败", "Connection failed")
    val SCRCPY_CLOSED_SHELL_STREAM = TextPair("已关闭 shell stream", "Closed shell stream")
    val SCRCPY_CLOSE_SHELL_STREAM_FAILED = TextPair("关闭 shell stream 失败", "Failed to close shell stream")
    val SCRCPY_REMOVED_ADB_FORWARD = TextPair("已移除 ADB forward", "Removed ADB forward")
    val SCRCPY_REMOVE_FORWARD_FAILED = TextPair("移除 forward 失败", "Failed to remove forward")
    val SCRCPY_TERMINATED_SERVER_PROCESS = TextPair("已终止 scrcpy-server 进程", "Terminated scrcpy-server process")
    val SCRCPY_TERMINATE_SERVER_FAILED = TextPair("终止 scrcpy-server 进程失败", "Failed to terminate scrcpy-server process")
    val SCRCPY_DISCONNECTED_ADB_KEPT = TextPair("Scrcpy 已断开，ADB 连接保持", "Scrcpy disconnected, ADB connection kept")
    
    // 关于页面
    val ABOUT_TITLE = TextPair("关于 Scrcpy Remote", "About Scrcpy Remote")
    val ABOUT_BASED_ON = TextPair("基于 Scrcpy", "Based on Scrcpy")
    val ABOUT_DESCRIPTION = TextPair(
        "Scrcpy Remote 是一款基于 ADB 协议的远程桌面工具，通常用于连接具有公网 IP 地址的服务或同一局域网内的服务。",
        "Scrcpy Remote is a remote desktop tool based on ADB protocol, typically used to connect to services with public IP addresses or services within the same local network."
    )
    val ABOUT_CONNECTION_TIP = TextPair(
        "如果无法正常连接到您的服务，请先检查网络连接是否正常。",
        "If you cannot connect to your service properly, please check if the network connection is normal first."
    )
    val ABOUT_HELP_TEXT = TextPair(
        "如果在使用过程中遇到问题并需要帮助，也可以加入我们的 Telegram 频道。",
        "If you encounter problems during use and need help, you can also join our Telegram channel."
    )
    val ABOUT_WECHAT_QR = TextPair("扫码添加微信", "Scan to add WeChat")
    val ABOUT_TELEGRAM_BUTTON = TextPair("Telegram 频道", "Telegram Channel")
    val ABOUT_PORTING_BUTTON = TextPair("软件：XRsec", "Software：XRSec")
}

/**
 * 文本对（中文+英文）
 */
data class TextPair(
    val chinese: String,
    val english: String
) {
    /**
     * 根据当前语言获取文本
     */
    fun get(): String {
        return LanguageManager.getText(chinese, english)
    }
}
