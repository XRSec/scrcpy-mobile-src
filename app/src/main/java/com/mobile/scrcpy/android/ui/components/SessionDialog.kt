package com.mobile.scrcpy.android.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.common.AppDimens
import com.mobile.scrcpy.android.common.AppTextSizes
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.PlaceholderTexts
import com.mobile.scrcpy.android.common.ScrcpyConstants
import com.mobile.scrcpy.android.common.rememberText
import com.mobile.scrcpy.android.core.data.model.DeviceGroup
import com.mobile.scrcpy.android.feature.session.SessionData
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.collections.isNotEmpty

/**
 * 格式化分组显示
 * 显示分组名称（最后一级），最多显示 3 个
 * 例如：
 * [] -> "未分组" / "Ungrouped"
 * ["id1"] -> "FRP"
 * ["id1", "id2"] -> "FRP, HZ"
 * ["id1", "id2", "id3"] -> "FRP, HZ, SH"
 * ["id1", "id2", "id3", "id4"] -> "FRP, HZ, SH +1"
 */
private fun formatGroupDisplay(selectedIds: List<String>, groups: List<DeviceGroup>): String {
    if (selectedIds.isEmpty()) {
        return BilingualTexts.GROUP_UNGROUPED.get()
    }
    
    val selectedGroups = groups.filter { selectedIds.contains(it.id) }
    val groupNames = selectedGroups.map { it.name }
    
    return when {
        groupNames.size <= 3 -> groupNames.joinToString(", ")
        else -> {
            val first3 = groupNames.take(3).joinToString(", ")
            val remaining = groupNames.size - 3
            "$first3 +$remaining"
        }
    }
}

/**
 * 编码器和编码格式映射工具
 */
object CodecMapper {
    /**
     * 根据编码器名称或 MIME 类型推断编码格式
     */
    fun getCodecFromEncoder(encoderNameOrMime: String): String? {
        val lower = encoderNameOrMime.lowercase()
        return when {
            // 视频编码器
            lower.contains("avc") || lower.contains("h264") || lower.contains("264") -> "h264"
            lower.contains("hevc") || lower.contains("h265") || lower.contains("265") -> "h265"
            lower.contains("av1") || lower.contains("av01") -> "av1"
            lower.contains("vp8") -> "vp8"
            lower.contains("vp9") -> "vp9"
            
            // 音频编码器
            lower.contains("opus") -> "opus"
            lower.contains("aac") || lower.contains("mp4a") -> "aac"
            lower.contains("flac") -> "flac"
            lower.contains("raw") || lower.contains("pcm") -> "raw"
            
            else -> null
        }
    }
    
    /**
     * 检查编码器是否匹配指定的编码格式
     */
    fun isEncoderMatchCodec(encoderName: String, codec: String): Boolean {
        if (encoderName.isEmpty()) return true // 空编码器（默认）总是匹配
        val detectedCodec = getCodecFromEncoder(encoderName)
        return detectedCodec == codec.lowercase()
    }
    
    /**
     * 将编码格式转换为大写显示
     */
    fun toDisplayFormat(codec: String): String {
        return codec.uppercase()
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
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
            color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(
                            0xFF959595
                        ),
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            modifier = Modifier.wrapContentWidth()
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 10.dp),
            textStyle = TextStyle(
                fontSize = 15.sp,
                lineHeight = 15.sp,
                color = if (isError) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.wrapContentWidth(),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isError) Color(0xFFFF3B30).copy(alpha = 0.6f) else Color(
                                0xFF959595
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun CompactSwitchRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun CompactClickableRow(
    text: String,
    trailingText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AppDimens.listItemHeight)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showArrow) {
                Text(
                    text = "›",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE5E5EA)
                )
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight", "DefaultLocale")
@Composable
fun AddSessionDialog(
    sessionData: SessionData? = null,
    availableGroups: List<DeviceGroup> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (SessionData) -> Unit
) {
    val isEditMode = sessionData != null

    var sessionName by remember { mutableStateOf(sessionData?.name ?: "") }
    var host by remember { mutableStateOf(sessionData?.host ?: "") }
    var port by remember { mutableStateOf(sessionData?.port ?: "") }
    var selectedGroupIds by remember { mutableStateOf(sessionData?.groupIds ?: emptyList()) }
    var showGroupSelector by remember { mutableStateOf(false) }
    
    // USB 相关状态
    var showUsbDeviceDialog by remember { mutableStateOf(false) }
    
    // 动态判断连接类型（基于 host 格式）
    val isUsbMode = host.startsWith("usb:")
    
    // 从 host 中提取 USB 序列号
    val usbSerialNumber = if (isUsbMode) {
        host.removePrefix("usb:")
    } else {
        ""
    }
    
    // 动态检测：当用户输入 "usb" 时自动切换到 USB 模式
    LaunchedEffect(host) {
        if (host.equals("usb", ignoreCase = true) && !showUsbDeviceDialog) {
            showUsbDeviceDialog = true  // 自动打开设备选择对话框
        }
    }

    var forceAdb by remember { mutableStateOf(sessionData?.forceAdb ?: false) }

    var maxSize by remember { mutableStateOf(sessionData?.maxSize ?: "") }
    var bitrate by remember { mutableStateOf(sessionData?.bitrate ?: "") }
    var maxFps by remember { mutableStateOf(sessionData?.maxFps ?: "") }
    var videoCodec by remember { mutableStateOf(sessionData?.videoCodec ?: "h264") }
    var videoEncoder by remember { mutableStateOf(sessionData?.videoEncoder ?: "") }
    var showVideoCodecMenu by remember { mutableStateOf(false) }
    var showEncoderOptionsDialog by remember { mutableStateOf(false) }

    var enableAudio by remember { mutableStateOf(sessionData?.enableAudio ?: false) }
    var audioCodec by remember { mutableStateOf(sessionData?.audioCodec ?: "aac") }
    var audioEncoder by remember { mutableStateOf(sessionData?.audioEncoder ?: "") }
    var audioVolume by remember {
        mutableFloatStateOf(
            sessionData?.audioBufferMs?.toFloatOrNull() ?: 1.0f
        )
    }
    var showAudioCodecMenu by remember { mutableStateOf(false) }
    var showAudioEncoderDialog by remember { mutableStateOf(false) }
    var stayAwake by remember { mutableStateOf(sessionData?.stayAwake ?: false) }  // 改为 false
    var turnScreenOff by remember { mutableStateOf(sessionData?.turnScreenOff ?: true) }
    var powerOffOnClose by remember { mutableStateOf(sessionData?.powerOffOnClose ?: false) }
    var keepDeviceAwake by remember { mutableStateOf(false) }
    var enableHardwareDecoding by remember { mutableStateOf(true) }
    var followRemoteOrientation by remember { mutableStateOf(false) }
    var showNewDisplay by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

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
                .wrapContentHeight(),
//                .height(dialogHeight),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                DialogHeader(
                    title = if (isEditMode) BilingualTexts.DIALOG_EDIT_SESSION.get() else BilingualTexts.DIALOG_CREATE_SESSION.get(),
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = BilingualTexts.BUTTON_CANCEL.get(),
                    rightButtonText = BilingualTexts.BUTTON_SAVE.get(),
                    rightButtonEnabled = if (isUsbMode) {
                        host.startsWith("usb:") && host.length > 4
                    } else {
                        host.isNotBlank()
                    },
                    onRightButtonClick = {
                        val isValid = if (isUsbMode) {
                            host.startsWith("usb:") && host.length > 4
                        } else {
                            host.isNotBlank()
                        }
                        
                        if (isValid) {
                            onConfirm(
                                SessionData(
                                    id = sessionData?.id ?: UUID.randomUUID().toString(),
                                    name = sessionName.ifBlank { host },
                                    host = host,
                                    port = if (isUsbMode) "" else port,
                                    color = sessionData?.color ?: "BLUE",
                                    forceAdb = forceAdb,
                                    maxSize = maxSize,
                                    bitrate = bitrate,
                                    maxFps = maxFps,
                                    videoCodec = videoCodec,
                                    videoEncoder = videoEncoder,
                                    enableAudio = enableAudio,
                                    audioCodec = audioCodec,
                                    audioEncoder = audioEncoder,
                                    audioBufferMs = String.format("%.1f", audioVolume),
                                    stayAwake = stayAwake,
                                    turnScreenOff = turnScreenOff,
                                    powerOffOnClose = powerOffOnClose,
                                    groupIds = selectedGroupIds
                                )
                            )
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    // 远程设备
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(BilingualTexts.SECTION_REMOTE_DEVICE.get())
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LabeledTextField(
                                    label = BilingualTexts.LABEL_SESSION_NAME.get(),
                                    value = sessionName,
                                    onValueChange = { sessionName = it },
                                    placeholder = BilingualTexts.PLACEHOLDER_SESSION_NAME.get()
                                )

                                AppDivider()
                                
                                // 根据 host 格式动态显示不同的输入方式
                                if (isUsbMode) {
                                    // USB 模式：显示设备选择
                                    CompactClickableRow(
                                        text = BilingualTexts.USB_SELECT_DEVICE.get(),
                                        trailingText = if (usbSerialNumber.isNotBlank()) 
                                            usbSerialNumber 
                                        else 
                                            BilingualTexts.USB_NO_DEVICE_SELECTED.get(),
                                        onClick = { showUsbDeviceDialog = true }
                                    )
                                } else {
                                    // TCP 模式：显示主机和端口
                                    LabeledTextField(
                                        label = BilingualTexts.LABEL_HOST.get(),
                                        value = host,
                                        onValueChange = { host = it },
                                        placeholder = PlaceholderTexts.HOST
                                    )

                                    AppDivider()

                                    LabeledTextField(
                                        label = BilingualTexts.LABEL_PORT.get(),
                                        value = port,
                                        onValueChange = { port = it },
                                        placeholder = PlaceholderTexts.PORT,
                                        keyboardType = KeyboardType.Number
                                    )
                                }
                                
                                // 分组选择
                                AppDivider()
                                
                                CompactClickableRow(
                                    text = BilingualTexts.GROUP_SELECT.get(),
                                    trailingText = formatGroupDisplay(selectedGroupIds, availableGroups),
                                    onClick = { showGroupSelector = true }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 连接选项
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(BilingualTexts.SECTION_CONNECTION_OPTIONS.get())
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            CompactSwitchRow(
                                text = BilingualTexts.SWITCH_FORCE_ADB.get(),
                                checked = forceAdb,
                                onCheckedChange = { forceAdb = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // ADB 会话选项
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(BilingualTexts.SECTION_ADB_SESSION_OPTIONS.get())
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                LabeledTextField(
                                    label = BilingualTexts.LABEL_MAX_SIZE.get(),
                                    value = maxSize,
                                    onValueChange = { maxSize = it },
                                    placeholder = BilingualTexts.HINT_EMPTY_USE_DEVICE_RESOLUTION.get(),
                                    keyboardType = KeyboardType.Number
                                )

                                AppDivider()

                                LabeledTextField(
                                    label = BilingualTexts.LABEL_BITRATE.get(),
                                    value = bitrate,
                                    onValueChange = { bitrate = it },
                                    placeholder = BilingualTexts.PLACEHOLDER_BITRATE.get()
                                )

                                AppDivider()

                                LabeledTextField(
                                    label = BilingualTexts.LABEL_MAX_FPS.get(),
                                    value = maxFps,
                                    onValueChange = { maxFps = it },
                                    placeholder = BilingualTexts.PLACEHOLDER_MAX_FPS.get(),
                                    keyboardType = KeyboardType.Number
                                )

                                AppDivider()

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(AppDimens.listItemHeight)
                                        .padding(horizontal = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        BilingualTexts.LABEL_VIDEO_CODEC.get(),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Box {
                                        Text(
                                            text = CodecMapper.toDisplayFormat(videoCodec),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.clickable {
                                                showVideoCodecMenu = true
                                            }
                                        )
                                        IOSStyledDropdownMenu(
                                            expanded = showVideoCodecMenu,
                                            onDismissRequest = {
                                                showVideoCodecMenu = false
                                            },
                                            modifier = Modifier.widthIn(
                                                min = 60.dp,
                                                max = 80.dp
                                            )
                                        ) {
                                            ScrcpyConstants.VIDEO_CODECS.forEach { codec ->
                                                IOSStyledDropdownMenuItem(
                                                    text = CodecMapper.toDisplayFormat(codec),
                                                    onClick = {
                                                        videoCodec = codec
                                                        // 检查当前编码器是否匹配新格式，不匹配则清空
                                                        if (!CodecMapper.isEncoderMatchCodec(videoEncoder, codec)) {
                                                            videoEncoder = ""
                                                        }
                                                        showVideoCodecMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                AppDivider()

                                CompactClickableRow(
                                    text = BilingualTexts.LABEL_VIDEO_ENCODER.get(),
                                    trailingText = when {
                                        host.isBlank() -> BilingualTexts.ENCODER_ERROR_INPUT_HOST.get()
                                        videoEncoder.isNotEmpty() -> videoEncoder
                                        else -> BilingualTexts.PLACEHOLDER_DEFAULT_ENCODER.get()
                                    },
                                    onClick = {
                                        if (host.isNotBlank()) {
                                            showEncoderOptionsDialog = true
                                        }
                                    },
                                    showArrow = host.isNotBlank()
                                )

                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_ENABLE_AUDIO.get(),
                                    checked = enableAudio,
                                    onCheckedChange = { enableAudio = it }
                                )

                                if (enableAudio) {
                                    AppDivider()

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(AppDimens.listItemHeight)
                                            .padding(horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            BilingualTexts.LABEL_AUDIO_CODEC.get(),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Box {
                                            Text(
                                                text = CodecMapper.toDisplayFormat(audioCodec),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.clickable {
                                                    showAudioCodecMenu = true
                                                }
                                            )
                                            IOSStyledDropdownMenu(
                                                expanded = showAudioCodecMenu,
                                                onDismissRequest = {
                                                    showAudioCodecMenu = false
                                                },
                                                modifier = Modifier.widthIn(
                                                    min = 60.dp,
                                                    max = 80.dp
                                                )
                                            ) {
                                                listOf(
                                                    "aac",
                                                    "opus",
                                                    "flac",
                                                    "raw"
                                                ).forEach { codec ->
                                                    IOSStyledDropdownMenuItem(
                                                        text = CodecMapper.toDisplayFormat(codec),
                                                        onClick = {
                                                            audioCodec = codec
                                                            // 检查当前编码器是否匹配新格式，不匹配则清空
                                                            if (!CodecMapper.isEncoderMatchCodec(audioEncoder, codec)) {
                                                                audioEncoder = ""
                                                            }
                                                            showAudioCodecMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    AppDivider()

                                    CompactClickableRow(
                                        text = BilingualTexts.LABEL_AUDIO_ENCODER.get(),
                                        trailingText = when {
                                            host.isBlank() -> BilingualTexts.ENCODER_ERROR_INPUT_HOST.get()
                                            audioEncoder.isNotEmpty() -> audioEncoder
                                            else -> BilingualTexts.PLACEHOLDER_DEFAULT_AUDIO_ENCODER.get()
                                        },
                                        onClick = {
                                            if (host.isNotBlank()) {
                                                showAudioEncoderDialog = true
                                            }
                                        },
                                        showArrow = host.isNotBlank()
                                    )

                                    AppDivider()

                                    // 音量缩放滑块
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(AppDimens.listItemHeight)
                                            .padding(horizontal = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            BilingualTexts.LABEL_AUDIO_VOLUME.get(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.width(80.dp)
                                        )
                                        Slider(
                                            value = audioVolume,
                                            onValueChange = { audioVolume = it },
                                            valueRange = 0.1f..2.0f,
                                            steps = 18,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFF007AFF),
                                                activeTrackColor = Color(0xFF007AFF),
                                                inactiveTrackColor = Color(0xFFE5E5EA)
                                            )
                                        )
                                        Text(
                                            "${String.format("%.1f", audioVolume)}x",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.width(50.dp),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        )
                                    }

                                    AppDivider()
                                }

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_STAY_AWAKE.get(),
                                    checked = stayAwake,
                                    onCheckedChange = { stayAwake = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_TURN_SCREEN_OFF.get(),
                                    checked = turnScreenOff,
                                    onCheckedChange = { turnScreenOff = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_POWER_OFF_ON_CLOSE.get(),
                                    checked = powerOffOnClose,
                                    onCheckedChange = { powerOffOnClose = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_KEEP_DEVICE_AWAKE.get(),
                                    checked = keepDeviceAwake,
                                    onCheckedChange = { keepDeviceAwake = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_ENABLE_HARDWARE_DECODING.get(),
                                    checked = enableHardwareDecoding,
                                    onCheckedChange = { enableHardwareDecoding = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_FOLLOW_ORIENTATION.get(),
                                    checked = followRemoteOrientation,
                                    onCheckedChange = { followRemoteOrientation = it }
                                )
                                AppDivider()

                                CompactSwitchRow(
                                    text = BilingualTexts.SWITCH_NEW_DISPLAY.get(),
                                    checked = showNewDisplay,
                                    onCheckedChange = { showNewDisplay = it }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 保存按钮
                    Button(
                        onClick = {
                            val isValid = if (isUsbMode) {
                                host.startsWith("usb:") && host.length > 4
                            } else {
                                host.isNotBlank()
                            }
                            
                            if (isValid) {
                                onConfirm(
                                    SessionData(
                                        id = sessionData?.id ?: UUID.randomUUID().toString(),
                                        name = sessionName.ifBlank { host },
                                        host = host,
                                        port = if (isUsbMode) "" else port,
                                        color = sessionData?.color ?: "BLUE",
                                        forceAdb = forceAdb,
                                        maxSize = maxSize,
                                        bitrate = bitrate,
                                        maxFps = maxFps,
                                        videoCodec = videoCodec,
                                        videoEncoder = videoEncoder,
                                        enableAudio = enableAudio,
                                        audioCodec = audioCodec,
                                        audioEncoder = audioEncoder,
                                        audioBufferMs = String.format("%.1f", audioVolume),
                                        stayAwake = stayAwake,
                                        turnScreenOff = turnScreenOff,
                                        powerOffOnClose = powerOffOnClose,
                                        groupIds = selectedGroupIds
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        enabled = if (isUsbMode) {
                            host.startsWith("usb:") && host.length > 4
                        } else {
                            host.isNotBlank()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Color(0xFF007AFF),
                            disabledContainerColor = MaterialTheme.colorScheme.surface,
                            disabledContentColor = Color(0xFF007AFF).copy(alpha = 0.3f)
                        )
                    ) {
                        val txtSaveButton = rememberText(
                            BilingualTexts.SESSION_SAVE_BUTTON.chinese,
                            BilingualTexts.SESSION_SAVE_BUTTON.english
                        )
                        Text(
                            text = txtSaveButton,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    if (showEncoderOptionsDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.VIDEO,
            host = host,
            port = port.ifBlank { "5555" },
            currentEncoder = videoEncoder,
            onDismiss = { showEncoderOptionsDialog = false },
            onEncoderSelected = { encoder ->
                videoEncoder = encoder
                // 自动同步视频编码格式
                if (encoder.isNotEmpty()) {
                    CodecMapper.getCodecFromEncoder(encoder)?.let { codec ->
                        videoCodec = codec
                    }
                }
                showEncoderOptionsDialog = false
            }
        )
    }

    if (showAudioEncoderDialog) {
        EncoderSelectionDialog(
            encoderType = EncoderType.AUDIO,
            host = host,
            port = port.ifBlank { "5555" },
            currentEncoder = audioEncoder,
            onDismiss = { showAudioEncoderDialog = false },
            onEncoderSelected = { encoder ->
                audioEncoder = encoder
                // 自动同步音频编码格式
                if (encoder.isNotEmpty()) {
                    CodecMapper.getCodecFromEncoder(encoder)?.let { codec ->
                        audioCodec = codec
                    }
                }
                showAudioEncoderDialog = false
            }
        )
    }

    // 分组选择对话框
    if (showGroupSelector) {
        GroupSelectorDialog(
            selectedGroupIds = selectedGroupIds,
            availableGroups = availableGroups,
            onGroupsSelected = { 
                selectedGroupIds = it
                showGroupSelector = false
            },
            onDismiss = { showGroupSelector = false }
        )
    }
    
    // USB 设备选择对话框
    if (showUsbDeviceDialog) {
        UsbDeviceSelectionDialog(
            currentSerialNumber = if (host.startsWith("usb:")) host.removePrefix("usb:") else "",
            onDeviceSelected = { serialNumber ->
                host = "usb:$serialNumber"
                showUsbDeviceDialog = false
            },
            onDismiss = { 
                // 如果用户取消选择且当前 host 是 "usb"，清空它
                if (host.equals("usb", ignoreCase = true)) {
                    host = ""
                }
                showUsbDeviceDialog = false
            }
        )
    }
}




/**
 * USB 设备选择对话框（用于会话编辑）
 */
@Composable
fun UsbDeviceSelectionDialog(
    currentSerialNumber: String,
    onDeviceSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val adbConnectionManager = remember { 
        com.mobile.scrcpy.android.app.ScreenRemoteApp.instance.adbConnectionManager 
    }
    
    val usbDevices by adbConnectionManager.getUsbDevices().collectAsState()
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // 自动扫描一次
    LaunchedEffect(Unit) {
        isScanning = true
        adbConnectionManager.scanUsbDevices()
        isScanning = false
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(AppDimens.WINDOW_WIDTH_RATIO)
                .wrapContentHeight(),
            shape = RoundedCornerShape(AppDimens.windowCornerRadius),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 使用通用 DialogHeader
                DialogHeader(
                    title = BilingualTexts.USB_SELECT_DEVICE.get(),
                    onDismiss = onDismiss,
                    showBackButton = false,
                    leftButtonText = BilingualTexts.BUTTON_CLOSE.get(),
                    trailingContent = {
                        // 右上角刷新按钮
                        androidx.compose.material3.IconButton(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    adbConnectionManager.scanUsbDevices()
                                    isScanning = false
                                }
                            },
                            enabled = !isScanning
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                                    contentDescription = BilingualTexts.LOG_REFRESH_BUTTON.get(),
                                    tint = com.mobile.scrcpy.android.common.AppColors.iOSBlue
                                )
                            }
                        }
                    }
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppDimens.paddingStandard)
                ) {
                    // 设备列表
                    if (usbDevices.isEmpty() && !isScanning) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = BilingualTexts.USB_NO_DEVICES_FOUND.get(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = AppTextSizes.body
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            usbDevices.forEach { deviceInfo ->
                                UsbDeviceSelectionItem(
                                    deviceInfo = deviceInfo,
                                    isSelected = deviceInfo.serialNumber == currentSerialNumber,
                                    onSelect = {
                                        // 如果没有权限，先请求权限
                                        if (!deviceInfo.hasPermission) {
                                            scope.launch {
                                                val permissionResult = adbConnectionManager.requestUsbPermission(deviceInfo.device)
                                                if (permissionResult.isSuccess) {
                                                    // 权限授予后，重新扫描设备列表
                                                    adbConnectionManager.scanUsbDevices()
                                                    // 只有序列号不为空才返回上一级
                                                    if (deviceInfo.serialNumber.isNotBlank()) {
                                                        onDeviceSelected(deviceInfo.serialNumber)
                                                    }
                                                } else {
                                                    // 权限被拒绝，显示提示
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        BilingualTexts.USB_PERMISSION_DENIED.get(),
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        } else {
                                            // 已有权限，只有序列号不为空才返回上一级
                                            if (deviceInfo.serialNumber.isNotBlank()) {
                                                onDeviceSelected(deviceInfo.serialNumber)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * USB 设备选择列表项
 */
@Composable
private fun UsbDeviceSelectionItem(
    deviceInfo: com.mobile.scrcpy.android.core.adb.usb.UsbDeviceInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimens.cardCornerRadius))
            .clickable { onSelect() },  // 所有设备都允许点击
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // USB 图标
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.Usb,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (deviceInfo.hasPermission) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 设备信息
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceInfo.getDisplayName(),
                    fontSize = AppTextSizes.body,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // 序列号为空时显示空字符串
                if (deviceInfo.serialNumber.isNotBlank()) {
                    Text(
                        text = "${BilingualTexts.USB_SERIAL_NUMBER.get()}: ${deviceInfo.serialNumber}",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "${BilingualTexts.USB_SERIAL_NUMBER.get()}: ",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (!deviceInfo.hasPermission) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${BilingualTexts.USB_PERMISSION_NOT_GRANTED_STATUS.get()} (${BilingualTexts.USB_CLICK_TO_REQUEST_PERMISSION.get()})",
                        fontSize = AppTextSizes.caption,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // 选中标记
            if (isSelected) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
