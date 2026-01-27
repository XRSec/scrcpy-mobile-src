package com.mobile.scrcpy.android.core.media

import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AudioDecoder - 音频解码器
 * 支持 Opus、AAC、FLAC、RAW 四种格式
 */
class AudioDecoder(private val volumeScale: Float = 1.0f) {
    private val decoderLock = Any()
    @Volatile private var decoder: MediaCodec? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var isRunning = false
    @Volatile private var isStopped = false

    var onConnectionLost: (() -> Unit)? = null  // 连接丢失回调

    suspend fun start(audioStream: AudioStream) =
        withContext(Dispatchers.IO) {
            try {
                val codec = audioStream.codec
                val sampleRate = audioStream.sampleRate
                val channelCount = audioStream.channelCount

                LogManager.d(LogTags.AUDIO_DECODER, "开始音频解码: codec=$codec, rate=$sampleRate, channels=$channelCount")

                isStopped = false
                isRunning = true

                // RAW 格式直接播放，不需要解码
                if (codec.lowercase() == "raw") {
                    playRawAudio(audioStream, sampleRate, channelCount)
                } else {
                    decodeAndPlay(audioStream, codec, sampleRate, channelCount)
                }

            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "音频解码失败: ${e.message}", e)
                // 检查是否是连接丢失
                if (e.message?.contains("Socket closed") == true ||
                    e.message?.contains("Stream closed") == true) {
                    LogManager.w(LogTags.AUDIO_DECODER, "音频连接丢失，触发回调")
                    onConnectionLost?.invoke()
                }
            } finally {
                stop()
            }
        }

    fun stop() {
        synchronized(decoderLock) {
            if (isStopped) {
                LogManager.d(LogTags.AUDIO_DECODER, "stop() 被调用，但已经停止，调用栈: ${Thread.currentThread().stackTrace.take(5).joinToString("\n")}")
                return
            }

            LogManager.d(LogTags.AUDIO_DECODER, "stop() 被调用，开始停止解码器，调用栈:\n${Thread.currentThread().stackTrace.take(8).joinToString("\n")}")

            isRunning = false
            isStopped = true

            try {
                audioTrack?.stop()
                audioTrack?.release()
            } catch (e: Exception) {
                // 忽略
            } finally {
                audioTrack = null
            }

            try {
                decoder?.stop()
                decoder?.release()
            } catch (e: Exception) {
                // 忽略
            } finally {
                decoder = null
            }

            LogManager.d(LogTags.AUDIO_DECODER, "音频解码器已停止")
        }
    }

    /**
     * RAW 格式直接播放
     */
    private fun playRawAudio(audioStream: AudioStream, sampleRate: Int, channelCount: Int) {
        val track = createAudioTrack(sampleRate, channelCount)
        if (track == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建 AudioTrack")
            return
        }

        audioTrack = track
        track.play()

        var packetCount = 0
        LogManager.d(LogTags.AUDIO_DECODER, "开始播放 RAW 音频 (音量: ${volumeScale}x)")

        while (isRunning) {
            try {
                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        packetCount++

                        // 应用音量缩放
                        val scaledData = if (volumeScale != 1.0f) {
                            applyVolumeScale(packet.payload, volumeScale)
                        } else {
                            packet.payload
                        }

                        // 写入 AudioTrack
                        val written = track.write(scaledData, 0, scaledData.size)

                        if (written < 0) {
                            LogManager.e(LogTags.AUDIO_DECODER, "AudioTrack 写入失败: $written")
                        } else if (packetCount <= 10 || packetCount % 100 == 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "RAW 音频包 #$packetCount: size=${scaledData.size}, written=$written")
                        }
                    }
                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }
            } catch (e: Exception) {
                if (isRunning && !isStopped) {
                    LogManager.e(LogTags.AUDIO_DECODER, "RAW 音频播放错误: ${e.message}", e)
                }
                break
            }
        }

        LogManager.d(LogTags.AUDIO_DECODER, "RAW 音频播放结束，共 $packetCount 包")
    }

    /**
     * 解码并播放（Opus/AAC/FLAC）
     */
    private fun decodeAndPlay(audioStream: AudioStream, codec: String, sampleRate: Int, channelCount: Int) {
        // 读取第一个包
        val firstPacket = audioStream.read()
        if (firstPacket !is dadb.AdbShellPacket.StdOut || firstPacket.payload.isEmpty()) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法读取第一个包")
            return
        }

        val firstData = firstPacket.payload
        LogManager.d(LogTags.AUDIO_DECODER, "第一个包: size=${firstData.size}, data=${firstData.take(16).joinToString(" ") { "%02X".format(it) }}...")

        // 判断是否为配置包
        var configData: ByteArray? = null
        var firstAudioPacket: ByteArray? = null

        if (codec.lowercase() == "opus") {
            // Opus: 检查是否为 OpusHead（19字节）
            if (firstData.size == 19 && String(firstData.copyOfRange(0, 8), Charsets.US_ASCII) == "OpusHead") {
                LogManager.d(LogTags.AUDIO_DECODER, "检测到 OpusHead 配置包")
                configData = firstData
            } else {
                // 裸 Opus 帧，不需要配置包
                LogManager.d(LogTags.AUDIO_DECODER, "检测到裸 Opus 帧，跳过配置包")
                firstAudioPacket = firstData
            }
        } else {
            // AAC/FLAC: 验证配置包
            if (validateConfigPacket(codec, firstData)) {
                configData = firstData
            } else {
                LogManager.e(LogTags.AUDIO_DECODER, "配置包格式错误")
                return
            }
        }

        // 创建解码器
        val createdDecoder = createDecoder(codec, sampleRate, channelCount, configData)
        if (createdDecoder == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建解码器")
            return
        }
        decoder = createdDecoder

        // 创建 AudioTrack
        val track = createAudioTrack(sampleRate, channelCount)
        if (track == null) {
            LogManager.e(LogTags.AUDIO_DECODER, "无法创建 AudioTrack")
            decoder?.release()
            decoder = null
            return
        }
        audioTrack = track
        track.play()

        LogManager.d(LogTags.AUDIO_DECODER, "开始解码循环")
        decodeLoop(audioStream, firstAudioPacket)
    }

    /**
     * 验证配置包格式
     */
    private fun validateConfigPacket(codec: String, data: ByteArray): Boolean {
        return when (codec.lowercase()) {
            "opus" -> {
                // OpusHead: 19 字节，以 "OpusHead" 开头
                if (data.size != 19) {
                    LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包大小错误: ${data.size}, 期望 19")
                    return false
                }
                val header = String(data.copyOfRange(0, 8), Charsets.US_ASCII)
                if (header != "OpusHead") {
                    LogManager.e(LogTags.AUDIO_DECODER, "Opus 配置包头错误: $header, 期望 OpusHead")
                    return false
                }

                // 详细解析并打印
                val version = data[8].toInt() and 0xFF
                val channels = data[9].toInt() and 0xFF
                val preSkip = (data[10].toInt() and 0xFF) or ((data[11].toInt() and 0xFF) shl 8)
                val sampleRate = (data[12].toInt() and 0xFF) or
                                ((data[13].toInt() and 0xFF) shl 8) or
                                ((data[14].toInt() and 0xFF) shl 16) or
                                ((data[15].toInt() and 0xFF) shl 24)
                val outputGain = (data[16].toInt() and 0xFF) or ((data[17].toInt() and 0xFF) shl 8)
                val channelMapping = data[18].toInt() and 0xFF

                LogManager.d(LogTags.AUDIO_DECODER, "OpusHead 详细: version=$version, channels=$channels, preSkip=$preSkip, sampleRate=$sampleRate, outputGain=$outputGain, channelMapping=$channelMapping")

                true
            }
            "aac" -> {
                // AudioSpecificConfig: 2 字节
                data.size == 2
            }
            "flac" -> {
                // STREAMINFO: 34 字节
                data.size == 34
            }
            else -> false
        }
    }

    /**
     * 创建解码器
     */
    private fun createDecoder(codec: String, sampleRate: Int, channelCount: Int, configData: ByteArray?): MediaCodec? {
        return try {
            val mime = when (codec.lowercase()) {
                "opus" -> MediaFormat.MIMETYPE_AUDIO_OPUS
                "aac" -> MediaFormat.MIMETYPE_AUDIO_AAC
                "flac" -> MediaFormat.MIMETYPE_AUDIO_FLAC
                else -> {
                    LogManager.e(LogTags.AUDIO_DECODER, "不支持的编码格式: $codec")
                    return null
                }
            }

            val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)

            // 设置配置数据（如果有）
            if (configData != null && configData.isNotEmpty()) {
                val csd0 = java.nio.ByteBuffer.wrap(configData)
                format.setByteBuffer("csd-0", csd0)
                LogManager.d(LogTags.AUDIO_DECODER, "${codec.uppercase()} 配置: csd-0=${configData.size}字节")
            } else {
                LogManager.d(LogTags.AUDIO_DECODER, "${codec.uppercase()}: 无配置数据，让解码器自动处理")
            }

            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)

            LogManager.d(LogTags.AUDIO_DECODER, "MediaFormat: $format")

            val mediaCodec = MediaCodec.createDecoderByType(mime)

            try {
                mediaCodec.configure(format, null, null, 0)
                mediaCodec.start()

                // 验证解码器状态
                try {
                    val testIndex = mediaCodec.dequeueInputBuffer(0)
                    if (testIndex < 0 && testIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        LogManager.e(LogTags.AUDIO_DECODER, "解码器状态异常: $testIndex")
                        mediaCodec.release()
                        return null
                    }
                    LogManager.d(LogTags.AUDIO_DECODER, "解码器状态验证成功")
                } catch (e: IllegalStateException) {
                    LogManager.e(LogTags.AUDIO_DECODER, "解码器状态验证失败: ${e.message}", e)
                    mediaCodec.release()
                    return null
                }

                LogManager.d(LogTags.AUDIO_DECODER, "解码器创建成功: ${mediaCodec.name}")
                return mediaCodec

            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "配置解码器失败: ${e.message}", e)
                try {
                    mediaCodec.release()
                } catch (ignored: Exception) {
                }
                return null
            }

        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "创建解码器失败: ${e.message}", e)
            null
        }
    }

    /**
     * 创建 AudioTrack
     */
    private fun createAudioTrack(sampleRate: Int, channelCount: Int): AudioTrack? {
        return try {
            val channelConfig = if (channelCount == 2) {
                AudioFormat.CHANNEL_OUT_STEREO
            } else {
                AudioFormat.CHANNEL_OUT_MONO
            }

            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4

            val track = AudioTrack.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            LogManager.d(LogTags.AUDIO_DECODER, "AudioTrack 创建成功: rate=$sampleRate, channels=$channelCount, bufferSize=$bufferSize")
            track
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "创建 AudioTrack 失败: ${e.message}", e)
            null
        }
    }

    /**
     * 解码循环
     */
    private fun decodeLoop(audioStream: AudioStream, firstAudioPacket: ByteArray? = null) {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        var inputCount = 0
        var outputCount = 0
        var pts = 0L  // 使用递增的时间戳

        LogManager.d(LogTags.AUDIO_DECODER, "解码循环开始")

        // 如果有第一个音频包，先处理它
        if (firstAudioPacket != null && firstAudioPacket.isNotEmpty()) {
            try {
                val currentDecoder = decoder
                if (currentDecoder != null && !isStopped) {
                    val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(firstAudioPacket)
                            currentDecoder.queueInputBuffer(
                                inputIndex,
                                0,
                                firstAudioPacket.size,
                                pts,
                                0
                            )
                            pts += 20000
                            inputCount++
                            frameCount++
                            LogManager.d(LogTags.AUDIO_DECODER, "已处理第一个音频包: size=${firstAudioPacket.size}")
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.e(LogTags.AUDIO_DECODER, "处理第一个音频包失败: ${e.message}", e)
            }
        }

        while (isRunning) {
            try {
                val currentDecoder = decoder
                if (currentDecoder == null || isStopped) break

                // 先尝试输出（避免缓冲区满）
                val drainedCount = drainOutputBuffers(bufferInfo)
                if (drainedCount > 0) {
                    outputCount += drainedCount
                    if (outputCount <= 10 || outputCount % 100 == 0) {
                        LogManager.d(LogTags.AUDIO_DECODER, "已输出 $outputCount 个音频缓冲区")
                    }
                }

                // 再输入数据
                when (val packet = audioStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        // 跳过静音包或无效包（Opus 有效帧至少 3 字节）
                        if (packet.payload.size < 3) {
                            if (frameCount < 10) {
                                LogManager.d(LogTags.AUDIO_DECODER, "跳过小包: size=${packet.payload.size}")
                            }
                            continue
                        }

                        frameCount++
                        if (frameCount <= 10 || frameCount % 100 == 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "音频帧 #$frameCount, size=${packet.payload.size}")
                        }

                        var shouldBreak = false
                        synchronized(decoderLock) {
                            if (decoder != currentDecoder || isStopped) {
                                shouldBreak = true
                            } else {
                                val inputIndex = currentDecoder.dequeueInputBuffer(10000)
                                if (inputIndex >= 0) {
                                    val inputBuffer = currentDecoder.getInputBuffer(inputIndex)
                                    if (inputBuffer != null) {
                                        inputBuffer.clear()
                                        inputBuffer.put(packet.payload)

                                        // Opus 每帧 20ms，48000Hz 采样率 = 960 samples
                                        // PTS 单位是微秒
                                        currentDecoder.queueInputBuffer(
                                            inputIndex,
                                            0,
                                            packet.payload.size,
                                            pts,
                                            0
                                        )
                                        pts += 20000  // 20ms = 20000us
                                        inputCount++

                                        if (inputCount <= 5 || inputCount % 100 == 0) {
                                            LogManager.d(LogTags.AUDIO_DECODER, "帧 #$frameCount 已送入解码器 (total=$inputCount, pts=${pts/1000}ms)")
                                        }
                                    }
                                }
                            }
                        }

                        if (shouldBreak) break
                    }
                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }

            } catch (e: IllegalStateException) {
                if (e.message?.contains("executing state") == true ||
                    e.message?.contains("Released state") == true) {
                    break
                }
                throw e
            } catch (e: Exception) {
                if (isRunning && !isStopped) {
                    LogManager.e(LogTags.AUDIO_DECODER, "解码错误: ${e.message}", e)
                }
                break
            }
        }

        // 最后再输出一次
        var finalDrainCount = 0
        while (drainOutputBuffers(bufferInfo) > 0 && finalDrainCount < 50) {
            finalDrainCount++
        }
        LogManager.d(LogTags.AUDIO_DECODER, "解码结束，共 $frameCount 帧输入，$outputCount 个缓冲区输出")
    }

    /**
     * 输出解码后的数据
     * @return 输出的缓冲区数量
     */
    private fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo): Int {
        if (isStopped) return 0

        val codec = decoder
        val track = audioTrack
        if (codec == null || track == null) return 0

        var drainedCount = 0

        try {
            // 使用较长的超时时间（100ms）等待第一个输出
            val timeout = if (drainedCount == 0) 100000L else 0L
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeout)
            var loopCount = 0

            // 调试：第一次调用时打印结果
            if (drainedCount == 0 && loopCount == 0) {
            when (outputIndex) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> LogManager.d(LogTags.AUDIO_DECODER, "⏳ 第一次 dequeue: INFO_TRY_AGAIN_LATER")
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> LogManager.d(LogTags.AUDIO_DECODER, "📋 第一次 dequeue: INFO_OUTPUT_FORMAT_CHANGED")
                else -> if (outputIndex >= 0) {
                    LogManager.d(LogTags.AUDIO_DECODER, "✅ 第一次 dequeue: 有效 index=$outputIndex")
                } else {
                    LogManager.d(LogTags.AUDIO_DECODER, "❓ 第一次 dequeue: 未知值=$outputIndex")
                }
            }
            }

            while (!isStopped && outputIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
                loopCount++

                when {
                    outputIndex >= 0 -> {
                        // 跳过配置缓冲区
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            LogManager.d(LogTags.AUDIO_DECODER, "跳过配置缓冲区")
                            codec.releaseOutputBuffer(outputIndex, false)
                            outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                            continue
                        }

                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            drainedCount++

                            // 应用音量缩放
                            if (volumeScale != 1.0f) {
                                applyVolumeScaleToBuffer(outputBuffer, bufferInfo.size, volumeScale)
                            }

                            // 写入 AudioTrack
                            val written = track.write(outputBuffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING)

                            if (written < 0) {
                                LogManager.e(LogTags.AUDIO_DECODER, "AudioTrack 写入失败: $written")
                            } else if (drainedCount <= 10 || drainedCount % 100 == 0) {
                                LogManager.d(LogTags.AUDIO_DECODER, "🔊 音频输出 #$drainedCount: size=${bufferInfo.size}, written=$written, pts=${bufferInfo.presentationTimeUs/1000}ms, volume=${volumeScale}x")
                            }
                        } else {
                            LogManager.w(LogTags.AUDIO_DECODER, "输出缓冲区为空或大小为0: buffer=$outputBuffer, size=${bufferInfo.size}")
                        }

                        codec.releaseOutputBuffer(outputIndex, false)
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        LogManager.d(LogTags.AUDIO_DECODER, "输出格式变化: $format")
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                    }
                    else -> break
                }

                if (loopCount > 100) {
                    LogManager.w(LogTags.AUDIO_DECODER, "drainOutputBuffers 循环过多，可能有问题")
                    break
                }
            }

            return drainedCount

        } catch (e: IllegalStateException) {
            if (e.message?.contains("executing state") == true ||
                e.message?.contains("Released state") == true) {
                return 0
            }
            throw e
        } catch (e: Exception) {
            LogManager.e(LogTags.AUDIO_DECODER, "输出数据异常: ${e.message}", e)
            return 0
        }
    }

    /**
     * 应用音量缩放到 PCM 数据
     * @param data PCM 16-bit 数据
     * @param scale 音量缩放系数 (0.1 ~ 2.0)
     * @return 缩放后的数据
     */
    private fun applyVolumeScale(data: ByteArray, scale: Float): ByteArray {
        if (scale == 1.0f) return data

        val scaledData = ByteArray(data.size)

        // PCM 16-bit 数据，每 2 个字节是一个样本
        for (i in 0 until data.size step 2) {
            if (i + 1 >= data.size) break

            // 读取 16-bit 样本 (小端序)
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()

            // 应用音量缩放
            var scaledSample = (sample * scale).toInt()

            // 限制在 16-bit 范围内，避免溢出
            scaledSample = scaledSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // 写回数据 (小端序)
            scaledData[i] = (scaledSample and 0xFF).toByte()
            scaledData[i + 1] = ((scaledSample shr 8) and 0xFF).toByte()
        }

        return scaledData
    }

    /**
     * 应用音量缩放到 ByteBuffer (PCM 16-bit)
     * @param buffer PCM 数据缓冲区
     * @param size 数据大小
     * @param scale 音量缩放系数 (0.1 ~ 2.0)
     */
    private fun applyVolumeScaleToBuffer(buffer: java.nio.ByteBuffer, size: Int, scale: Float) {
        if (scale == 1.0f) return

        val position = buffer.position()

        // PCM 16-bit 数据，每 2 个字节是一个样本
        for (i in 0 until size step 2) {
            if (i + 1 >= size) break

            // 读取 16-bit 样本 (小端序)
            val byte1 = buffer.get(position + i).toInt() and 0xFF
            val byte2 = buffer.get(position + i + 1).toInt()
            val sample = ((byte2 shl 8) or byte1).toShort()

            // 应用音量缩放
            var scaledSample = (sample * scale).toInt()

            // 限制在 16-bit 范围内，避免溢出
            scaledSample = scaledSample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

            // 写回数据 (小端序)
            buffer.put(position + i, (scaledSample and 0xFF).toByte())
            buffer.put(position + i + 1, ((scaledSample shr 8) and 0xFF).toByte())
        }
    }
}

/**
 * 音频流接口
 */
interface AudioStream : AutoCloseable {
    val codec: String
    val sampleRate: Int
    val channelCount: Int
    fun read(): dadb.AdbShellPacket
}
