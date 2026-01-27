package com.mobile.scrcpy.android.ui.screens

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mobile.scrcpy.android.common.BilingualTexts
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import com.mobile.scrcpy.android.common.TTSManager
import com.mobile.scrcpy.android.ui.components.DialogHeader
import com.mobile.scrcpy.android.ui.components.IOSStyledDropdownMenu
import com.mobile.scrcpy.android.ui.components.IOSStyledDropdownMenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.math.sin


data class CodecInfo(
    val name: String,
    val type: String,
    val isEncoder: Boolean,
    val capabilities: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodecTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 双语文本 - 使用 .get() 直接获取，避免订阅延迟导致的闪烁
    val txtTitle = BilingualTexts.CODEC_TEST_TITLE.get()
    val txtDone = BilingualTexts.BUTTON_DONE.get()
    val txtTestButton = BilingualTexts.CODEC_TEST_BUTTON.get()
    val txtSearchPlaceholder = BilingualTexts.CODEC_TEST_SEARCH_PLACEHOLDER.get()
    val txtFoundCount = BilingualTexts.CODEC_TEST_FOUND_COUNT.get()
    val txtAudioCodecs = BilingualTexts.CODEC_TEST_AUDIO_CODECS.get()
    val txtWarningOpus = BilingualTexts.CODEC_TEST_WARNING_OPUS.get()
    val txtInfoCompatibility = BilingualTexts.CODEC_TEST_INFO_COMPATIBILITY.get()
    val txtTypeLabel = BilingualTexts.CODEC_TEST_TYPE_LABEL.get()
    val txtEncoder = BilingualTexts.CODEC_TEST_ENCODER.get()
    val txtDecoder = BilingualTexts.CODEC_TEST_DECODER.get()
    val txtFilterAll = BilingualTexts.CODEC_TEST_FILTER_ALL.get()
    val txtSampleRate = BilingualTexts.CODEC_TEST_SAMPLE_RATE.get()
    val txtMaxChannels = BilingualTexts.CODEC_TEST_MAX_CHANNELS.get()
    val txtActual = BilingualTexts.CODEC_TEST_ACTUAL.get()
    val txtNoDetails = BilingualTexts.CODEC_TEST_NO_DETAILS.get()

    // 获取编解码器列表
    val allCodecs = remember {
        getAudioCodecs(txtSampleRate, txtMaxChannels, txtActual, txtNoDetails)
    }

    var searchText by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf(FilterType.ALL) }
    var codecTypeFilter by remember { mutableStateOf(CodecTypeFilter.ALL) }
    var testingCodec by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 懒加载 TTS：仅在需要时初始化
    LaunchedEffect(Unit) {
        if (!TTSManager.isReady()) {
            LogManager.d(LogTags.CODEC_TEST_SCREEN, "懒加载 TTS 管理器")
            TTSManager.init(context)
        }
    }

    val filteredCodecs = remember(searchText, filterType, codecTypeFilter, allCodecs) {
        allCodecs.filter { codec ->
            val matchesSearch = searchText.isEmpty() ||
                codec.name.contains(searchText, ignoreCase = true) ||
                codec.type.contains(searchText, ignoreCase = true)

            val matchesFilter = when (filterType) {
                FilterType.ALL -> true
                FilterType.DECODER -> !codec.isEncoder
                FilterType.ENCODER -> codec.isEncoder
            }

            val matchesCodecType = when (codecTypeFilter) {
                CodecTypeFilter.ALL -> true
                CodecTypeFilter.OPUS -> codec.type == "audio/opus"
                CodecTypeFilter.AAC -> codec.type == "audio/mp4a-latm"
                CodecTypeFilter.FLAC -> codec.type == "audio/flac"
                CodecTypeFilter.RAW -> codec.type == "audio/raw"
            }

            matchesSearch && matchesFilter && matchesCodecType
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val dialogHeight = screenHeight * 0.8f

    Dialog(
        onDismissRequest = onBack,
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
                    title = txtTitle,
                    onDismiss = onBack,
                    showBackButton = true,
                    rightButtonText = txtDone,
                    onRightButtonClick = onBack
                )

                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 搜索框和下拉菜单
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 搜索框
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
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
                                            text = txtSearchPlaceholder,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        // 编解码器类型筛选
                        var codecTypeExpanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { codecTypeExpanded = true },
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = Color(0xFF007AFF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    when (codecTypeFilter) {
                                        CodecTypeFilter.ALL -> txtFilterAll
                                        CodecTypeFilter.OPUS -> "OPUS"
                                        CodecTypeFilter.AAC -> "AAC"
                                        CodecTypeFilter.FLAC -> "FLAC"
                                        CodecTypeFilter.RAW -> "RAW"
                                    },
                                    fontSize = 13.sp,
                                )
                            }

                            IOSStyledDropdownMenu(
                                expanded = codecTypeExpanded,
                                onDismissRequest = { codecTypeExpanded = false },
                                modifier = Modifier.width(70.dp)
                            ) {
                                IOSStyledDropdownMenuItem(
                                    text = txtFilterAll,
                                    onClick = {
                                        codecTypeFilter = CodecTypeFilter.ALL
                                        codecTypeExpanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = "OPUS",
                                    onClick = {
                                        codecTypeFilter = CodecTypeFilter.OPUS
                                        codecTypeExpanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = "AAC",
                                    onClick = {
                                        codecTypeFilter = CodecTypeFilter.AAC
                                        codecTypeExpanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = "FLAC",
                                    onClick = {
                                        codecTypeFilter = CodecTypeFilter.FLAC
                                        codecTypeExpanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = "RAW",
                                    onClick = {
                                        codecTypeFilter = CodecTypeFilter.RAW
                                        codecTypeExpanded = false
                                    }
                                )
                            }
                        }

                        // 编码器/解码器筛选
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            Button(
                                onClick = { expanded = true },
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(38.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = Color(0xFF007AFF)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    when (filterType) {
                                        FilterType.ALL -> txtFilterAll
                                        FilterType.DECODER -> txtDecoder
                                        FilterType.ENCODER -> txtEncoder
                                    },
                                    fontSize = 13.sp,
                                )
                            }

                            IOSStyledDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.width(80.dp)
                            ) {
                                IOSStyledDropdownMenuItem(
                                    text = txtFilterAll,
                                    onClick = {
                                        filterType = FilterType.ALL
                                        expanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = txtDecoder,
                                    onClick = {
                                        filterType = FilterType.DECODER
                                        expanded = false
                                    }
                                )
                                IOSStyledDropdownMenuItem(
                                    text = txtEncoder,
                                    onClick = {
                                        filterType = FilterType.ENCODER
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        "$txtFoundCount ${filteredCodecs.size} $txtAudioCodecs",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    // 提示信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3CD), RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            txtWarningOpus,
                            fontSize = 12.sp,
                            color = Color(0xFF856404),
                            lineHeight = 16.sp
                        )
                        Text(
                            txtInfoCompatibility,
                            fontSize = 12.sp,
                            color = Color(0xFF856404),
                            lineHeight = 16.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredCodecs.forEach { codec ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !codec.isEncoder && testingCodec == null) {
                                    scope.launch {
                                        testingCodec = codec.name
                                        testAudioDecoder(codec.type, codec.name, TTSManager.getInstance())
                                        testingCodec = null
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (testingCodec == codec.name) Color(0xFFE3F2FD) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        codec.name,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (!codec.isEncoder && testingCodec == null) {
                                        Text(
                                            txtTestButton,
                                            fontSize = 12.sp,
                                            color = Color(0xFF007AFF)
                                        )
                                    } else if (testingCodec == codec.name) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                }
                                Text(
                                    "$txtTypeLabel: ${codec.type} | ${if (codec.isEncoder) txtEncoder else txtDecoder}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (codec.capabilities.isNotEmpty()) {
                                    Text(
                                        codec.capabilities,
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666)
                                    )
                                }

                                // Opus 解码器兼容性警告
                                if (codec.type == "audio/opus" && !codec.isEncoder) {
                                    Text(
                                        txtWarningOpus,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF6B00)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

enum class FilterType {
    ALL, DECODER, ENCODER
}

enum class CodecTypeFilter {
    ALL, OPUS, AAC, FLAC, RAW
}

private fun getAudioCodecs(
    txtSampleRate: String,
    txtMaxChannels: String,
    txtActual: String,
    txtNoDetails: String
): List<CodecInfo> {
    val result = mutableListOf<CodecInfo>()

    try {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

        for (codecInfo in codecList.codecInfos) {
            val supportedTypes = codecInfo.supportedTypes

            for (type in supportedTypes) {
                // 只列出音频相关的
                if (type.startsWith("audio/")) {
                    val capabilities = try {
                        val caps = codecInfo.getCapabilitiesForType(type)
                        buildString {
                            // 采样率范围
                            val audioC = caps.audioCapabilities
                            if (audioC != null) {
                                append("$txtSampleRate: ")
                                val rates = audioC.supportedSampleRateRanges
                                if (rates != null && rates.isNotEmpty()) {
                                    val lower = rates.first().lower
                                    val upper = rates.first().upper

                                    // 修复 Opus 编解码器的采样率显示 bug
                                    if (type == "audio/opus" && lower == 8000 && upper == 8000) {
                                        append("8000-48000 Hz ($txtActual)")
                                    } else {
                                        append("$lower-$upper Hz")
                                    }
                                }

                                // 声道数
                                val maxChannels = audioC.maxInputChannelCount
                                append(" | $txtMaxChannels: $maxChannels")
                            }
                        }
                    } catch (_: Exception) {
                        txtNoDetails
                    }

                    result.add(
                        CodecInfo(
                            name = codecInfo.name,
                            type = type,
                            isEncoder = codecInfo.isEncoder,
                            capabilities = capabilities
                        )
                    )
                }
            }
        }
        // 按类型和名称排序
        result.sortWith(compareBy({ it.type }, { it.name }))


        for (codecInfo in result) {
            // 打印 Opus 解码器信息
            if (codecInfo.name.contains("opus", ignoreCase = true) && !codecInfo.isEncoder) {
                LogManager.d(LogTags.CODEC_TEST_SCREEN, "Opus 解码器: ${codecInfo.name}, 类型: ${codecInfo.type}, 能力: ${codecInfo.capabilities}")
            }
        }

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "找到 ${result.size} 个音频编解码器")
    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "获取编解码器列表失败: ${e.message}", e)
    }

    return result
}

/**
 * 测试音频解码器 - 用 TTS 生成音频，通过指定编解码器测试
 */
private suspend fun testAudioDecoder(mimeType: String, decoderName: String, tts: TextToSpeech?) = withContext(Dispatchers.IO) {
    try {
        LogManager.d(LogTags.CODEC_TEST_SCREEN, "开始测试解码器: $mimeType 解码器: $decoderName")

        // 生成 TTS PCM 数据
        val ttsResult = generateTTSAudio(tts)
        var pcmData: ByteArray
        var ttsSampleRate: Int
        var ttsChannelCount: Int

        if (ttsResult != null) {
            pcmData = ttsResult.first
            ttsSampleRate = ttsResult.second
            ttsChannelCount = ttsResult.third
            LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 原始音频: $ttsSampleRate Hz, $ttsChannelCount 声道, ${pcmData.size} 字节")
        } else {
            LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 生成失败，使用哔声")
            ttsSampleRate = 48000
            ttsChannelCount = 2
            pcmData = generateBeep(sampleRate = ttsSampleRate, channels = ttsChannelCount)
        }

        // 根据编解码器选择合适的采样率
        val targetSampleRate = when (mimeType) {
            "audio/3gpp" -> 8000      // AMR-NB 只支持 8000 Hz
            "audio/amr-wb" -> 16000   // AMR-WB 只支持 16000 Hz
            else -> 48000             // 其他使用 48000 Hz
        }

        // 统一声道数
        val targetChannelCount = if (mimeType.startsWith("audio/amr")) 1 else 2

        // 重采样 PCM 数据
        if (ttsSampleRate != targetSampleRate || ttsChannelCount != targetChannelCount) {
            LogManager.d(LogTags.CODEC_TEST_SCREEN, "重采样: $ttsSampleRate Hz -> $targetSampleRate Hz, $ttsChannelCount -> $targetChannelCount 声道")
            pcmData = resamplePCM(pcmData, ttsSampleRate, ttsChannelCount, targetSampleRate, targetChannelCount)
        }

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "目标格式: $targetSampleRate Hz, $targetChannelCount 声道, ${pcmData.size} 字节")

        // RAW 格式直接播放
        if (mimeType == "audio/raw") {
            playRawAudio(pcmData, targetSampleRate, targetChannelCount)
            return@withContext
        }

        // 创建编码器
        val encoder = MediaCodec.createEncoderByType(mimeType)
        val encoderFormat = MediaFormat.createAudioFormat(mimeType, targetSampleRate, targetChannelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 128000)
            when (mimeType) {
                MediaFormat.MIMETYPE_AUDIO_AAC -> {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                }
                MediaFormat.MIMETYPE_AUDIO_FLAC -> {
                    setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5)
                }
            }
        }
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        // 编码
        val encodedData = mutableListOf<ByteArray>()
        var csdData: ByteArray? = null
        var inputDone = false
        var outputDone = false
        var inputOffset = 0

        val bufferInfo = MediaCodec.BufferInfo()

        while (!outputDone) {
            // 输入
            if (!inputDone) {
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val remaining = pcmData.size - inputOffset
                        val size = minOf(remaining, inputBuffer.remaining())

                        if (size > 0) {
                            inputBuffer.put(pcmData, inputOffset, size)
                            encoder.queueInputBuffer(inputIndex, 0, size, 0, 0)
                            inputOffset += size
                        } else if (inputOffset >= pcmData.size) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }
            }

            // 输出
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        outputBuffer.get(data)

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            csdData = data
                        } else {
                            encodedData.add(data)
                        }
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = encoder.outputFormat
                    if (csdData == null) {
                        try {
                            val csd0 = format.getByteBuffer("csd-0")
                            if (csd0 != null) {
                                csdData = ByteArray(csd0.remaining())
                                csd0.get(csdData)
                            }
                        } catch (e: Exception) {
                            // 忽略
                        }
                    }
                }
            }
        }

        encoder.stop()
        encoder.release()

        // 创建解码器
        val decoder = MediaCodec.createDecoderByType(mimeType)
        val decoderFormat = MediaFormat.createAudioFormat(mimeType, targetSampleRate, targetChannelCount)

        if (csdData != null) {
            decoderFormat.setByteBuffer("csd-0", ByteBuffer.wrap(csdData))
        }

        decoder.configure(decoderFormat, null, null, 0)
        decoder.start()

        // 创建 AudioTrack
        val channelConfig = if (targetChannelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val bufferSize = AudioTrack.getMinBufferSize(targetSampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
        val audioTrack = AudioTrack.Builder()
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(targetSampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        // 解码并播放
        var frameIndex = 0
        inputDone = false
        outputDone = false
        var presentationTimeUs = 0L

        while (!outputDone) {
            // 输入编码数据
            if (!inputDone) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    if (frameIndex < encodedData.size) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(encodedData[frameIndex])
                            decoder.queueInputBuffer(inputIndex, 0, encodedData[frameIndex].size, presentationTimeUs, 0)
                            // 根据实际数据大小计算时间戳（假设每帧约 20ms，但不强制）
                            presentationTimeUs += 20000L
                            frameIndex++
                        }
                    } else {
                        // 所有数据已输入，发送 EOS
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    }
                }
            }

            // 输出解码数据
            val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        audioTrack.write(outputBuffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING)
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        }

        // 等待播放完成
        Thread.sleep(500)

        audioTrack.stop()
        audioTrack.release()
        decoder.stop()
        decoder.release()

        LogManager.d(LogTags.CODEC_TEST_SCREEN, "✅ 测试成功: $mimeType")

    } catch (e: Exception) {
        LogManager.e(LogTags.CODEC_TEST_SCREEN, "❌ 测试失败: $mimeType 解码器: $decoderName - ${e.message}", e)
    }
}

/**
 * 播放 RAW PCM 音频
 */
private fun playRawAudio(pcmData: ByteArray, sampleRate: Int, channelCount: Int) {
    val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
    val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT) * 2
    val audioTrack = AudioTrack.Builder()
        .setAudioFormat(AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build())
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    audioTrack.play()
    audioTrack.write(pcmData, 0, pcmData.size)
    Thread.sleep(1500)
    audioTrack.stop()
    audioTrack.release()

    LogManager.d(LogTags.CODEC_TEST_SCREEN, "✅ RAW 音频播放完成")
}

/**
 * 使用 TTS 生成 "Hello World" 音频，返回 (PCM数据, 采样率, 声道数)
 */
private suspend fun generateTTSAudio(tts: TextToSpeech?): Triple<ByteArray, Int, Int>? =
    withContext(Dispatchers.IO) {
        if (tts == null) return@withContext null

        val file = java.io.File.createTempFile("tts", ".wav")
        try {
            // 同步等待 TTS 完成
            var done = false
            @Suppress("OVERRIDE_DEPRECATION")
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { done = true }
                override fun onError(utteranceId: String?) { done = true }
            })

            val result = tts.synthesizeToFile("Hello World", null, file, "tts_gen")
            if (result == TextToSpeech.SUCCESS) {
                // 等待文件生成
                var waitCount = 0
                while (!done && waitCount < 50) {
                    delay(100)
                    waitCount++
                }

                if (file.exists() && file.length() > 44) {
                    val wavData = file.readBytes()

                    // 解析 WAV 头
                    val buffer = ByteBuffer.wrap(wavData).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buffer.position(22) // 声道数位置
                    val channels = buffer.short.toInt()
                    val sampleRate = buffer.int

                    // 跳过 WAV 头（44 字节）
                    val pcmData = wavData.copyOfRange(44, wavData.size)

                    val duration = pcmData.size / (sampleRate * channels * 2.0)
                    LogManager.d(LogTags.CODEC_TEST_SCREEN, "TTS 生成音频: $sampleRate Hz, $channels 声道, ${pcmData.size} 字节 (${"%.2f".format(duration)} 秒)")

                    return@withContext Triple(pcmData, sampleRate, channels)
                }
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.CODEC_TEST_SCREEN, "TTS 生成失败: ${e.message}", e)
        } finally {
            file.delete()
        }
        return@withContext null
    }

/**
 * 生成哔声
 */
private fun generateBeep(frequency: Double = 800.0, sampleRate: Int, channels: Int, duration: Double = 0.5): ByteArray {
    val numSamples = (sampleRate * duration).toInt()
    val buffer = ByteBuffer.allocate(numSamples * channels * 2)

    for (i in 0 until numSamples) {
        val sample = (sin(2.0 * Math.PI * frequency * i / sampleRate) * 32767).toInt().toShort()
        // repeat(channels) {
        for (ch in 0 until channels) {
            buffer.putShort(sample)
        }
    }

    return buffer.array()
}

/**
 * 简单的 PCM 重采样（线性插值）
 */
private fun resamplePCM(
    input: ByteArray,
    srcRate: Int,
    srcChannels: Int,
    dstRate: Int,
    dstChannels: Int
): ByteArray {
    val srcSamples = input.size / (srcChannels * 2)
    val dstSamples = (srcSamples * dstRate.toDouble() / srcRate).toInt()
    val output = ByteBuffer.allocate(dstSamples * dstChannels * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    val inputBuffer = ByteBuffer.wrap(input).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    for (i in 0 until dstSamples) {
        val srcPos = i * srcRate.toDouble() / dstRate
        val srcIndex = srcPos.toInt()

        if (srcIndex < srcSamples - 1) {
            // 线性插值
            val frac = srcPos - srcIndex

            for (ch in 0 until dstChannels) {
                val srcCh = if (ch < srcChannels) ch else 0

                val pos1 = (srcIndex * srcChannels + srcCh) * 2
                val pos2 = ((srcIndex + 1) * srcChannels + srcCh) * 2

                val sample1 = inputBuffer.getShort(pos1).toInt()
                val sample2 = inputBuffer.getShort(pos2).toInt()

                val interpolated = (sample1 + (sample2 - sample1) * frac).toInt().toShort()
                output.putShort(interpolated)
            }
        } else {
            // 最后一个样本，直接复制
            for (ch in 0 until dstChannels) {
                val srcCh = if (ch < srcChannels) ch else 0
                val pos = (srcIndex * srcChannels + srcCh) * 2
                output.putShort(inputBuffer.getShort(pos))
            }
        }
    }

    return output.array()
}
