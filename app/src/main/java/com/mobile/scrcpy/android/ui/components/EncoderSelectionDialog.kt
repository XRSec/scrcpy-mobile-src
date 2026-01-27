package com.mobile.scrcpy.android.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.core.adb.AdbConnectionManager
import com.mobile.scrcpy.android.ui.screens.CodecTestScreen
import kotlinx.coroutines.launch

/**
 * 编码器类型枚举
 */
enum class EncoderType {
    VIDEO,  // 视频编码器
    AUDIO   // 音频编码器
}

/**
 * 编码器信息接口（统一视频和音频编码器）
 */
interface EncoderInfo {
    val name: String
    val mimeType: String
}

/**
 * 紧凑型文本输入框（内部使用）
 */
@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        textStyle = TextStyle(
            fontSize = 15.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF959595),
                    )
                }
                innerTextField()
            }
        }
    )
}

/**
 * 通用编码器选择对话框
 * 
 * @param encoderType 编码器类型（视频或音频）
 * @param host 设备主机地址
 * @param port 设备端口
 * @param currentEncoder 当前选中的编码器名称
 * @param onDismiss 关闭对话框回调
 * @param onEncoderSelected 选择编码器回调
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun EncoderSelectionDialog(
    encoderType: EncoderType,
    host: String,
    port: String,
    currentEncoder: String = "",
    onDismiss: () -> Unit,
    onEncoderSelected: (String) -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    var selectedEncoder by remember { mutableStateOf(currentEncoder) }
    var customEncoderName by remember { mutableStateOf(currentEncoder) }
    var detectedEncoders by remember { mutableStateOf<List<EncoderInfo>>(emptyList()) }
    var isDetecting by remember { mutableStateOf(false) }
    var detectError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val adbConnectionManager = remember { AdbConnectionManager.getInstance(context) }

    // 根据编码器类型获取配置
    val config = remember(encoderType) {
        when (encoderType) {
            EncoderType.VIDEO -> EncoderDialogConfig(
                title = BilingualTexts.DIALOG_SELECT_VIDEO_ENCODER.get(),
                sectionTitle = BilingualTexts.SECTION_DETECTED_ENCODERS.get(),
                detectingStatus = BilingualTexts.STATUS_DETECTING_VIDEO_ENCODERS.get(),
                noEncodersStatus = BilingualTexts.STATUS_NO_ENCODERS_DETECTED.get(),
                filterOptions = buildList {
                    add(BilingualTexts.ENCODER_FILTER_ALL.get())
                    add("H264")
                    add("H265")
                    if (ApiCompatHelper.isAV1Supported()) {
                        add("AV1")
                    }
                },
                showCodecTest = false
            )
            EncoderType.AUDIO -> EncoderDialogConfig(
                title = BilingualTexts.DIALOG_SELECT_AUDIO_ENCODER.get(),
                sectionTitle = BilingualTexts.SECTION_DETECTED_AUDIO_ENCODERS.get(),
                detectingStatus = BilingualTexts.STATUS_DETECTING_AUDIO_ENCODERS.get(),
                noEncodersStatus = BilingualTexts.STATUS_NO_AUDIO_ENCODERS_DETECTED.get(),
                filterOptions = listOf(
                    BilingualTexts.ENCODER_FILTER_ALL.get(),
                    "OPUS",
                    "AAC",
                    "FLAC",
                    "RAW"
                ),
                showCodecTest = true
            )
        }
    }

    // 检测编码器的函数
    fun detectEncoders() {
        if (host.isBlank()) {
            detectError = BilingualTexts.ENCODER_ERROR_INPUT_HOST.get()
            return
        }

        scope.launch {
            isDetecting = true
            detectError = null

            try {
                val deviceId = "$host:${port.ifBlank { "5555" }}"
                val connectResult = adbConnectionManager.connectDevice(host, port.toIntOrNull() ?: 5555)

                if (connectResult.isFailure) {
                    detectError = "${BilingualTexts.ERROR_CONNECTION_FAILED.get()}: ${connectResult.exceptionOrNull()?.message}"
                    isDetecting = false
                    return@launch
                }

                val connection = adbConnectionManager.getConnection(deviceId)
                if (connection == null) {
                    detectError = BilingualTexts.ERROR_CANNOT_GET_CONNECTION.get()
                    isDetecting = false
                    return@launch
                }

                // 根据类型检测不同的编码器
                when (encoderType) {
                    EncoderType.VIDEO -> {
                        val result = connection.detectVideoEncoders(context)
                        if (result.isSuccess) {
                            detectedEncoders = result.getOrNull() ?: emptyList()
                            if (detectedEncoders.isEmpty()) {
                                detectError = config.noEncodersStatus
                            }
                        } else {
                            detectError = "${BilingualTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
                        }
                    }
                    EncoderType.AUDIO -> {
                        val result = connection.detectAudioEncoders(context)
                        if (result.isSuccess) {
                            detectedEncoders = result.getOrNull() ?: emptyList()
                            if (detectedEncoders.isEmpty()) {
                                detectError = config.noEncodersStatus
                            }
                        } else {
                            detectError = "${BilingualTexts.ERROR_DETECTION_FAILED.get()}: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                detectError = "${BilingualTexts.ERROR_DETECTION_EXCEPTION.get()}: ${e.message}"
            } finally {
                isDetecting = false
            }
        }
    }

    // 自动检测一次
    LaunchedEffect(Unit) {
        detectEncoders()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = config.title,
                    onDismiss = onDismiss,
                    showBackButton = true,
                    rightButtonText = BilingualTexts.BUTTON_DONE.get(),
                    onRightButtonClick = {
                        val encoder = when {
                            selectedEncoder.isNotEmpty() -> selectedEncoder
                            customEncoderName.isNotEmpty() -> customEncoderName
                            else -> ""
                        }
                        onEncoderSelected(encoder)
                        onDismiss()
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    // 搜索和筛选状态
                    var searchText by remember { mutableStateOf("") }
                    var codecTypeFilter by remember { mutableStateOf(config.filterOptions.first()) }
                    
                    // 编码器选项
                    SectionTitle(BilingualTexts.SECTION_ENCODER_OPTIONS.get())
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            // 默认编码器
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(AppDimens.listItemHeight)
                                    .clickable {
                                        selectedEncoder = ""
                                        customEncoderName = ""
                                    }
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    BilingualTexts.LABEL_DEFAULT_ENCODER.get(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (selectedEncoder.isEmpty() && customEncoderName.isEmpty()) {
                                    Text("✓", color = Color(0xFF007AFF), fontSize = 18.sp)
                                }
                            }

                            AppDivider()

                            // 自定义编码器名称
                            CompactTextField(
                                value = customEncoderName,
                                onValueChange = {
                                    customEncoderName = it
                                    selectedEncoder = ""
                                },
                                placeholder = BilingualTexts.PLACEHOLDER_CUSTOM_ENCODER.get()
                            )
                            
                            // 音频编码器特有：测试音频解码器
                            if (config.showCodecTest) {
                                AppDivider()
                                
                                var showCodecTest by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppDimens.listItemHeight)
                                        .clickable { showCodecTest = true }
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        BilingualTexts.LABEL_TEST_AUDIO_DECODER.get(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "›",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 20.sp
                                    )
                                }
                                
                                if (showCodecTest) {
                                    CodecTestScreen(onBack = { showCodecTest = false })
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 检测到的编码器
                    SectionTitle(config.sectionTitle)

                    when {
                        isDetecting -> {
                            DetectingCard(
                                status = config.detectingStatus,
                                host = host,
                                port = port
                            )
                        }
                        detectError != null -> {
                            ErrorCard(error = detectError!!)
                        }
                        detectedEncoders.isEmpty() -> {
                            EmptyCard(message = config.noEncodersStatus)
                        }
                        else -> {
                            EncoderListSection(
                                encoders = detectedEncoders,
                                searchText = searchText,
                                onSearchTextChange = { searchText = it },
                                codecTypeFilter = codecTypeFilter,
                                onCodecTypeFilterChange = { codecTypeFilter = it },
                                filterOptions = config.filterOptions,
                                selectedEncoder = selectedEncoder,
                                onEncoderSelected = { encoder ->
                                    selectedEncoder = encoder.name
                                    customEncoderName = encoder.name
                                },
                                encoderType = encoderType
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 刷新编码器按钮
                    Button(
                        onClick = { detectEncoders() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        enabled = !isDetecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF007AFF),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(BilingualTexts.ENCODER_REFRESH_BUTTON.get())
                    }
                }
            }
        }
    }
}

/**
 * 编码器对话框配置
 */
private data class EncoderDialogConfig(
    val title: String,
    val sectionTitle: String,
    val detectingStatus: String,
    val noEncodersStatus: String,
    val filterOptions: List<String>,
    val showCodecTest: Boolean
)

/**
 * 检测中卡片
 */
@Composable
private fun DetectingCard(
    status: String,
    host: String,
    port: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF007AFF)
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${BilingualTexts.STATUS_CONNECTING.get()} $host:${port.ifBlank { "5555" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 错误卡片
 */
@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                BilingualTexts.STATUS_DETECTION_FAILED.get(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 空状态卡片
 */
@Composable
private fun EmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 编码器列表区域
 */
@Composable
private fun EncoderListSection(
    encoders: List<EncoderInfo>,
    searchText: String,
    onSearchTextChange: (String) -> Unit,
    codecTypeFilter: String,
    onCodecTypeFilterChange: (String) -> Unit,
    filterOptions: List<String>,
    selectedEncoder: String,
    onEncoderSelected: (EncoderInfo) -> Unit,
    encoderType: EncoderType
) {
    // 搜索和筛选
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 搜索框
        BasicTextField(
            value = searchText,
            onValueChange = onSearchTextChange,
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp),
            textStyle = TextStyle(
                fontSize = 14.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = BilingualTexts.PLACEHOLDER_SEARCH_ENCODER.get(),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
        
        // 编码器类型筛选下拉菜单
        var codecTypeExpanded by remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { codecTypeExpanded = true },
                modifier = Modifier
                    .width(80.dp)
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = Color(0xFF007AFF)
                ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(codecTypeFilter, fontSize = 13.sp)
            }

            IOSStyledDropdownMenu(
                expanded = codecTypeExpanded,
                onDismissRequest = { codecTypeExpanded = false },
                modifier = Modifier.width(80.dp)
            ) {
                filterOptions.forEach { type ->
                    IOSStyledDropdownMenuItem(
                        text = type,
                        onClick = {
                            onCodecTypeFilterChange(type)
                            codecTypeExpanded = false
                        }
                    )
                }
            }
        }
    }
    
    Spacer(modifier = Modifier.height(10.dp))
    
    // 应用筛选
    val filteredEncoders = encoders.filter { encoder ->
        val matchesSearch = searchText.isEmpty() || 
            encoder.name.contains(searchText, ignoreCase = true) ||
            encoder.mimeType.contains(searchText, ignoreCase = true)
        
        val matchesCodecType = when (encoderType) {
            EncoderType.VIDEO -> when (codecTypeFilter) {
                filterOptions[0] -> true  // 全部
                "H264" -> encoder.mimeType.contains("avc", ignoreCase = true) || 
                         encoder.mimeType.contains("h264", ignoreCase = true)
                "H265" -> encoder.mimeType.contains("hevc", ignoreCase = true) || 
                         encoder.mimeType.contains("h265", ignoreCase = true)
                "AV1" -> encoder.mimeType.contains("av1", ignoreCase = true) ||
                        encoder.mimeType.contains("av01", ignoreCase = true)
                else -> true
            }
            EncoderType.AUDIO -> when (codecTypeFilter) {
                filterOptions[0] -> true  // 全部
                "OPUS" -> encoder.mimeType.contains("opus", ignoreCase = true)
                "AAC" -> encoder.mimeType.contains("aac", ignoreCase = true) ||
                        encoder.mimeType.contains("mp4a", ignoreCase = true)
                "FLAC" -> encoder.mimeType.contains("flac", ignoreCase = true)
                "RAW" -> encoder.mimeType.contains("raw", ignoreCase = true)
                else -> true
            }
        }
        
        matchesSearch && matchesCodecType
    }
    
    // 编码器列表
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            filteredEncoders.forEachIndexed { index, encoder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onEncoderSelected(encoder) }
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            encoder.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            encoder.mimeType,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (selectedEncoder == encoder.name) {
                        Text("✓", color = Color(0xFF007AFF), fontSize = 18.sp)
                    }
                }
                if (index < filteredEncoders.size - 1) {
                    AppDivider()
                }
            }
        }
    }
}
