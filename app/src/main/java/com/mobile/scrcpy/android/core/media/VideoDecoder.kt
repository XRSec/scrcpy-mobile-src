package com.mobile.scrcpy.android.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import com.mobile.scrcpy.android.common.ApiCompatHelper
import com.mobile.scrcpy.android.feature.scrcpy.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import com.mobile.scrcpy.android.common.LogManager
import com.mobile.scrcpy.android.common.LogTags

/**
 * VideoDecoder - 视频解码器
 */
class VideoDecoder(
    private var surface: Surface?,  // 改为可空类型
    private val videoCodec: String = "h264",
    cachedDecoderName: String? = null  // 从配置传入的缓存解码器名称
) {
    private var decoder: MediaCodec? = null
    private var isRunning = false
    private var isStopped = false
    private var selectedDecoderName: String? = cachedDecoderName  // 使用传入的缓存
    private val surfaceLock = Any()  // ✅ 同步锁，防止并发问题
    private var isSurfaceBound = false  // ✅ 标记 Surface 是否真正绑定成功

    // ✅ 关键：dummy Surface 用于后台占位
    private var dummySurface: Surface? = null
    private var dummySurfaceTexture: android.graphics.SurfaceTexture? = null

    var onVideoSizeChanged: ((width: Int, height: Int, rotation: Int) -> Unit)? = null
    var onDecoderSelected: ((decoderName: String) -> Unit)? = null  // 回调通知选中的解码器
    var onConnectionLost: (() -> Unit)? = null  // 连接丢失回调（Socket closed / Stream closed）

    private var currentWidth = 0
    private var currentHeight = 0
    private var currentRotation = 0

    private companion object {
        const val H264_NAL_SPS = 7
        const val H264_NAL_PPS = 8
        const val H264_NAL_IDR = 5

        const val H265_NAL_VPS = 32
        const val H265_NAL_SPS = 33
        const val H265_NAL_PPS = 34
        const val H265_NAL_IDR_W_RADL = 19
        const val H265_NAL_IDR_N_LP = 20

        const val BUFFER_SIZE = 10 * 1024 * 1024
        const val FRAME_DURATION_US = 33333L
        const val FRAME_META_MIN_SIZE = 6
        const val FRAME_META_MAX_SIZE = 10
    }

    private val mimeType: String
        get() {
            val mime = ApiCompatHelper.getVideoMimeType(videoCodec.lowercase())
            return mime ?: MediaFormat.MIMETYPE_VIDEO_AVC  // 默认使用 H264
        }

    suspend fun start(videoStream: VideoStream, width: Int, height: Int) =
        withContext(Dispatchers.IO) {
            try {
                LogManager.d(LogTags.VIDEO_DECODER, "开始解码 $videoCodec: ${width}x${height}")

                // ✅ 创建 dummy Surface（必须在 configure 前）
                createDummySurface()

                isStopped = false
                currentWidth = width
                currentHeight = height
                currentRotation = 0
                onVideoSizeChanged?.invoke(width, height, 0)

                decoder = createDecoder(width, height) ?: run {
                    LogManager.e(LogTags.VIDEO_DECODER, "无法创建解码器")
                    return@withContext
                }
                LogManager.d(LogTags.VIDEO_DECODER, "解码器: ${decoder?.name}")

                isRunning = true
                decodeLoop(videoStream)

            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "解码失败: ${e.message}", e)
            } finally {
                stop()
            }
        }

    fun stop() {
        if (isStopped) {
            LogManager.d(LogTags.VIDEO_DECODER, "解码器已停止，跳过")
            return
        }

        isRunning = false
        isStopped = true

        try {
            decoder?.stop()
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "停止解码器失败: ${e.message}", e)
        }

        // ✅ 释放 dummy Surface
        releaseDummySurface()
    }

    /**
     * 创建 dummy Surface（用于后台占位）
     */
    private fun createDummySurface() {
        try {
            dummySurfaceTexture = android.graphics.SurfaceTexture(0).apply {
                setDefaultBufferSize(1, 1)
            }
            dummySurface = Surface(dummySurfaceTexture)
            LogManager.d(LogTags.VIDEO_DECODER, "✓ Dummy Surface 已创建")
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "创建 dummy Surface 失败: ${e.message}")
        }
    }

    /**
     * 释放 dummy Surface
     */
    private fun releaseDummySurface() {
        try {
            dummySurface?.release()
            dummySurface = null
            dummySurfaceTexture?.release()
            dummySurfaceTexture = null
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "释放 dummy Surface 失败: ${e.message}")
        }
    }

    /**
     * 动态切换 Surface（支持后台时设置为 null）
     * @param newSurface 新的 Surface，null 表示切换到 dummy Surface
     */
    fun setSurface(newSurface: Surface?) {
        synchronized(surfaceLock) {
            try {
                // ✅ 检查解码器状态
                val codec = decoder
                if (codec == null || isStopped) {
                    LogManager.w(LogTags.VIDEO_DECODER, "解码器未运行，跳过 Surface 切换")
                    return
                }

                surface = newSurface  // 更新内部引用

                // ✅ 关键：根据 Surface 是否为 null 切换到真实或 dummy Surface
                val targetSurface = newSurface ?: dummySurface

                if (targetSurface != null) {
                    codec.setOutputSurface(targetSurface)
                    isSurfaceBound = (newSurface != null)

                    if (newSurface != null) {
                        LogManager.d(LogTags.VIDEO_DECODER, "✅ Surface 已切换（恢复渲染）")
                    } else {
                        LogManager.d(LogTags.VIDEO_DECODER, "✅ 已切换到 dummy Surface（后台模式）")
                    }
                } else {
                    LogManager.e(LogTags.VIDEO_DECODER, "无法切换 Surface：dummy Surface 不可用")
                }
            } catch (e: IllegalStateException) {
                // 忽略状态异常（可能解码器已停止）
                LogManager.w(LogTags.VIDEO_DECODER, "切换 Surface 失败（状态异常）: ${e.message}")
            } catch (e: Exception) {
                LogManager.e(LogTags.VIDEO_DECODER, "切换 Surface 失败: ${e.message}", e)
            }
        }
    }

    /**
     * 创建解码器 - 优先使用缓存，避免重复检测
     */
    private fun createDecoder(width: Int, height: Int): MediaCodec? {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height)

            // 1. 优先使用缓存的解码器
            selectedDecoderName?.let { cachedName ->
                try {
                    LogManager.d(LogTags.VIDEO_DECODER, "使用缓存解码器: $cachedName")
                    return MediaCodec.createByCodecName(cachedName)
                } catch (_: Exception) {
                    LogManager.w(LogTags.VIDEO_DECODER, "缓存解码器失效: $cachedName, 重新检测")
                    selectedDecoderName = null
                }
            }

            // 2. 缓存失效或不存在，开始检测（仅在必要时执行）
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)

            // 系统推荐
            codecList.findDecoderForFormat(format)?.let { name ->
                val info = codecList.codecInfos.firstOrNull { it.name == name }
                if (info != null && isLikelyHardware(info)) {
                    selectedDecoderName = name
                    onDecoderSelected?.invoke(name)  // 通知外部保存缓存
                    LogManager.d(LogTags.VIDEO_DECODER, "系统推荐: $name")
                    return MediaCodec.createByCodecName(name)
                }
            }

            // 手动选择硬件解码器
            for (info in codecList.codecInfos) {
                if (info.isEncoder || !info.supportedTypes.contains(mimeType)) continue
                if (!isLikelyHardware(info) || info.name.contains("goldfish", true)) continue

                try {
                    selectedDecoderName = info.name
                    onDecoderSelected?.invoke(info.name)  // 通知外部保存缓存
                    LogManager.d(LogTags.VIDEO_DECODER, "硬件解码: ${info.name}")
                    return MediaCodec.createByCodecName(info.name)
                } catch (_: Exception) {}
            }

            // 回退
            LogManager.w(LogTags.VIDEO_DECODER, "使用默认解码器")
            return MediaCodec.createDecoderByType(mimeType)

        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "创建解码器失败", e)
            return null
        }
    }

    private fun isLikelyHardware(info: MediaCodecInfo): Boolean {
        return ApiCompatHelper.isHardwareAccelerated(info)
    }


    /**
     * 统一解码循环 - 消除重复代码
     */
    private fun decodeLoop(videoStream: VideoStream) {
        val bufferInfo = MediaCodec.BufferInfo()
        var configured = false
        val nalBuffer = ByteBuffer.allocate(BUFFER_SIZE)
        var frameCount = 0
        var pts = 0L

        LogManager.d(LogTags.VIDEO_DECODER, "解码循环开始: $videoCodec")

        while (isRunning) {
            try {
                if (configured) {
                    drainOutputBuffers(bufferInfo)
                }

                when (val packet = videoStream.read()) {
                    is dadb.AdbShellPacket.StdOut -> {
                        if (packet.payload.isEmpty()) continue

                        // 处理 Frame Meta
                        if (packet.payload.size in FRAME_META_MIN_SIZE..FRAME_META_MAX_SIZE &&
                            !isNalStartCode(packet.payload)) {
                            handleFrameMeta(packet.payload)
                            continue
                        }

                        nalBuffer.put(packet.payload)
                    }
                    is dadb.AdbShellPacket.Exit -> break
                    else -> continue
                }

                // 根据编码格式处理
                when (videoCodec.lowercase()) {
                    "h264" -> {
                        val nalUnit = extractNalUnit(nalBuffer) ?: continue
                        val nalType = nalUnit[4].toInt() and 0x1F

                        when {
                            nalType == H264_NAL_SPS -> {
                                val ppsNal = extractNalUnit(nalBuffer)
                                if (ppsNal != null && (ppsNal[4].toInt() and 0x1F) == H264_NAL_PPS) {
                                    if (configured) {
                                        reconfigureDecoderH264(currentWidth, currentHeight, nalUnit, ppsNal)
                                    } else {
                                        configureDecoderH264(currentWidth, currentHeight, nalUnit, ppsNal)
                                        configured = true
                                    }
                                }
                            }
                            configured && nalType != H264_NAL_PPS -> {
                                frameCount++
                                pts += FRAME_DURATION_US
                                
                                // ✅ 检测关键帧，用于调试后台恢复性能
                                if (nalType == H264_NAL_IDR) {
                                    LogManager.d(LogTags.VIDEO_DECODER, "🎯 收到关键帧 (IDR) #$frameCount")
                                }
                                
                                decodeFrame(nalUnit, pts, nalType == H264_NAL_IDR)
                            }
                        }
                    }
                    "h265", "hevc" -> {
                        val nalUnit = extractNalUnit(nalBuffer) ?: continue
                        val nalType = (nalUnit[4].toInt() and 0x7E) shr 1

                        when {
                            nalType == H265_NAL_VPS -> {
                                val spsNal = extractNalUnit(nalBuffer)
                                val ppsNal = extractNalUnit(nalBuffer)
                                if (spsNal != null && ppsNal != null) {
                                    if (configured) {
                                        reconfigureDecoderH265(currentWidth, currentHeight, nalUnit, spsNal, ppsNal)
                                    } else {
                                        configureDecoderH265(currentWidth, currentHeight, nalUnit, spsNal, ppsNal)
                                        configured = true
                                    }
                                }
                            }
                            configured && nalType !in listOf(H265_NAL_SPS, H265_NAL_PPS) -> {
                                frameCount++
                                pts += FRAME_DURATION_US
                                val isKeyFrame = nalType == H265_NAL_IDR_W_RADL || nalType == H265_NAL_IDR_N_LP
                                
                                // ✅ 检测关键帧，用于调试后台恢复性能
                                if (isKeyFrame) {
                                    LogManager.d(LogTags.VIDEO_DECODER, "🎯 收到关键帧 (H265 IDR) #$frameCount")
                                }
                                
                                decodeFrame(nalUnit, pts, isKeyFrame)
                            }
                        }
                    }
                    "av1" -> {
                        if (nalBuffer.position() > 0) {
                            nalBuffer.flip()
                            val frameData = ByteArray(nalBuffer.remaining())
                            nalBuffer.get(frameData)
                            nalBuffer.clear()

                            if (!configured) {
                                reconfigureDecoderAV1(currentWidth, currentHeight, frameData)
                                configured = true
                            } else {
                                frameCount++
                                pts += FRAME_DURATION_US
                                decodeFrame(frameData, pts, false)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                if (isRunning) {
                    when {
                        e.message?.contains("Stream closed") == true -> {
                            LogManager.w(LogTags.VIDEO_DECODER, "视频流已关闭，触发连接丢失处理")
                            onConnectionLost?.invoke()  // 通知上层连接丢失
                        }
                        e.message?.contains("Socket closed") == true -> {
                            LogManager.w(LogTags.VIDEO_DECODER, "Socket 已关闭，触发连接丢失处理")
                            onConnectionLost?.invoke()  // 通知上层连接丢失
                        }
                        e.message?.contains("Read timed out") == true ->
                            LogManager.w(LogTags.VIDEO_DECODER, "视频流超时（设备息屏），继续等待...")
                        else -> {
                            LogManager.e(LogTags.VIDEO_DECODER, "解码错误: ${e.message}", e)
                            onConnectionLost?.invoke()  // 其他异常也触发连接丢失
                        }
                    }
                }
                break
            }
        }

        LogManager.d(LogTags.VIDEO_DECODER, "解码结束，共 $frameCount 帧")
    }

    /**
     * 处理 Frame Meta 消息
     */
    private fun handleFrameMeta(data: ByteArray) {
        try {
            if (data.size < FRAME_META_MIN_SIZE) return

            val width = ((data[1].toInt() and 0xFF) shl 8) or (data[2].toInt() and 0xFF)
            val height = ((data[3].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
            val rotation = data[5].toInt() and 0xFF

            if (width != currentWidth || height != currentHeight || rotation != currentRotation) {
                LogManager.d(LogTags.VIDEO_DECODER, "视频参数变化: ${currentWidth}x${currentHeight}@${currentRotation}° -> ${width}x${height}@${rotation}°")

                currentWidth = width
                currentHeight = height
                currentRotation = rotation

                onVideoSizeChanged?.invoke(width, height, rotation)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解析 Frame Meta 失败: ${e.message}")
        }
    }

    /**
     * 提取 NAL 单元 - 优化性能
     */
    private fun extractNalUnit(buffer: ByteBuffer): ByteArray? {
        if (buffer.position() < 4) return null

        buffer.flip()

        var startPos = -1
        val limit = buffer.limit()

        // 查找第一个起始码
        for (i in 0 until limit - 3) {
            if (buffer.get(i) == 0.toByte() &&
                buffer.get(i + 1) == 0.toByte() &&
                buffer.get(i + 2) == 0.toByte() &&
                buffer.get(i + 3) == 1.toByte()) {
                startPos = i
                break
            }
        }

        if (startPos < 0) {
            buffer.compact()
            return null
        }

        // 查找下一个起始码
        var endPos = -1
        for (i in startPos + 4 until limit - 3) {
            if (buffer.get(i) == 0.toByte() &&
                buffer.get(i + 1) == 0.toByte() &&
                buffer.get(i + 2) == 0.toByte() &&
                buffer.get(i + 3) == 1.toByte()) {
                endPos = i
                break
            }
        }

        val nalSize = if (endPos > 0) endPos - startPos else limit - startPos
        val nalUnit = ByteArray(nalSize)
        buffer.position(startPos)
        buffer.get(nalUnit)

        if (endPos > 0) {
            buffer.position(endPos)
            buffer.compact()
        } else {
            buffer.clear()
        }

        return nalUnit
    }



    private fun isNalStartCode(data: ByteArray): Boolean {
        return data.size >= 4 &&
                data[0] == 0.toByte() &&
                data[1] == 0.toByte() &&
                data[2] == 0.toByte() &&
                data[3] == 1.toByte()
    }

    /**
     * 从输出格式获取真实视频尺寸
     */
    private fun updateVideoSizeFromOutputFormat(outputFormat: MediaFormat) {
        try {
            val cropRect = ApiCompatHelper.getCropRectIfSupported(outputFormat)

            val realWidth: Int
            val realHeight: Int

            if (cropRect != null) {
                realWidth = cropRect.right - cropRect.left + 1
                realHeight = cropRect.bottom - cropRect.top + 1
            } else {
                realWidth = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                realHeight = outputFormat.getInteger(MediaFormat.KEY_HEIGHT)
            }

            if (realWidth != currentWidth || realHeight != currentHeight) {
                currentWidth = realWidth
                currentHeight = realHeight
                currentRotation = if (realWidth > realHeight) 90 else 0

                LogManager.d(LogTags.VIDEO_DECODER, "视频尺寸: ${realWidth}x${realHeight}")
                onVideoSizeChanged?.invoke(realWidth, realHeight, currentRotation)
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "获取输出格式失败: ${e.message}")
        }
    }

    /**
     * 配置 H.264 解码器
     */
    private fun configureDecoderH264(width: Int, height: Int, sps: ByteArray, pps: ByteArray) {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))

            applyLowLatencyConfig(format)

            // ✅ 关键：configure 时必须传 Surface（真实或 dummy）
            val initialSurface = synchronized(surfaceLock) {
                if (surface != null && surface!!.isValid) {
                    surface
                } else {
                    dummySurface  // 后台启动时使用 dummy
                }
            }

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置解码器：没有可用的 Surface")
                return
            }

            decoder?.configure(format, initialSurface, null, 0)
            decoder?.start()

            // 标记绑定状态
            synchronized(surfaceLock) {
                isSurfaceBound = (surface != null && surface!!.isValid)
                if (isSurfaceBound) {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ 解码器配置完成，Surface 已绑定")
                } else {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ 解码器配置完成（使用 dummy Surface）")
                }
            }

            // 从输出格式获取真实尺寸
            decoder?.outputFormat?.let { updateVideoSizeFromOutputFormat(it) }

        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 H.264 解码器（屏幕旋转/分辨率变化）
     */
    private fun reconfigureDecoderH264(width: Int, height: Int, sps: ByteArray, pps: ByteArray) {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "🔄 重新配置解码器")

            // 停止旧解码器
            decoder?.stop()
            decoder?.release()
            decoder = null

            // 创建新解码器（使用 SPS 中的容器尺寸）
            decoder = createDecoder(width, height)
            if (decoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return
            }

            // 配置新解码器（会自动从 crop 获取真实宽高）
            configureDecoderH264(width, height, sps, pps)

        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置解码器失败: ${e.message}", e)
        }
    }

    /**
     * 配置 H.265 解码器
     */
    private fun configureDecoderH265(width: Int, height: Int, vps: ByteArray, sps: ByteArray, pps: ByteArray) {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height)
            format.setByteBuffer("csd-0", ByteBuffer.wrap(vps))
            format.setByteBuffer("csd-1", ByteBuffer.wrap(sps))
            format.setByteBuffer("csd-2", ByteBuffer.wrap(pps))

            applyLowLatencyConfig(format)

            val initialSurface = synchronized(surfaceLock) {
                if (surface != null && surface!!.isValid) surface else dummySurface
            }

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置 H.265 解码器：没有可用的 Surface")
                return
            }

            decoder?.configure(format, initialSurface, null, 0)
            decoder?.start()

            synchronized(surfaceLock) {
                isSurfaceBound = (surface != null && surface!!.isValid)
                if (isSurfaceBound) {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ H.265 解码器配置完成，Surface 已绑定")
                } else {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ H.265 解码器配置完成（使用 dummy Surface）")
                }
            }
//                }
//            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置 H.265 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 H.265 解码器（屏幕旋转/分辨率变化）
     */
    private fun reconfigureDecoderH265(width: Int, height: Int, vps: ByteArray, sps: ByteArray, pps: ByteArray) {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "🔄 重新配置 H.265 解码器: ${width}x${height}")

            decoder?.stop()
            decoder?.release()
            decoder = null

            decoder = createDecoder(width, height)
            if (decoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return
            }

            configureDecoderH265(width, height, vps, sps, pps)

            LogManager.d(LogTags.VIDEO_DECODER, "✓ H.265 解码器重新配置完成")
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置 H.265 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 配置 AV1 解码器
     */
    private fun configureDecoderAV1(width: Int, height: Int, configData: ByteArray) {
        try {
            val format = MediaFormat.createVideoFormat(mimeType, width, height)
            // AV1 的配置数据在第一帧中，MediaCodec 会自动处理

            applyLowLatencyConfig(format)

            val initialSurface = synchronized(surfaceLock) {
                if (surface != null && surface!!.isValid) surface else dummySurface
            }

            if (initialSurface == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法配置 AV1 解码器：没有可用的 Surface")
                return
            }

            decoder?.configure(format, initialSurface, null, 0)
            decoder?.start()

            synchronized(surfaceLock) {
                isSurfaceBound = (surface != null && surface!!.isValid)
                if (isSurfaceBound) {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ AV1 解码器配置完成，Surface 已绑定")
                } else {
                    LogManager.d(LogTags.VIDEO_DECODER, "✓ AV1 解码器配置完成（使用 dummy Surface）")
                }
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "配置 AV1 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 重新配置 AV1 解码器（屏幕旋转/分辨率变化）
     */
    private fun reconfigureDecoderAV1(width: Int, height: Int, configData: ByteArray) {
        try {
            LogManager.d(LogTags.VIDEO_DECODER, "🔄 重新配置 AV1 解码器: ${width}x${height}")

            decoder?.stop()
            decoder?.release()
            decoder = null

            decoder = createDecoder(width, height)
            if (decoder == null) {
                LogManager.e(LogTags.VIDEO_DECODER, "无法创建新解码器")
                return
            }

            configureDecoderAV1(width, height, configData)

            LogManager.d(LogTags.VIDEO_DECODER, "✓ AV1 解码器重新配置完成")
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "重新配置 AV1 解码器失败: ${e.message}", e)
        }
    }

    /**
     * 应用低延迟配置（通用）
     */
    private fun applyLowLatencyConfig(format: MediaFormat) {
        ApiCompatHelper.setLowLatencyIfSupported(format, 1)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())

        ApiCompatHelper.setAllowFrameDropIfSupported(format, 0)
    }

    /**
     * 🔥 优化的解码帧方法 - 减少超时，提高响应速度
     */
    private fun decodeFrame(frameData: ByteArray, pts: Long, isKeyFrame: Boolean) {
        // ✅ 检查解码器状态
        if (isStopped || decoder == null) return
        
        try {
            val inputIndex = decoder?.dequeueInputBuffer(0) ?: -1 // 非阻塞
            if (inputIndex < 0) return

            val inputBuffer = decoder?.getInputBuffer(inputIndex)
            inputBuffer?.clear()
            inputBuffer?.put(frameData)

            val flags = if (isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            decoder?.queueInputBuffer(inputIndex, 0, frameData.size, pts / 1000, flags)

        } catch (e: IllegalStateException) {
            // 解码器已释放，忽略
            if (!isStopped) {
                LogManager.w(LogTags.VIDEO_DECODER, "解码器状态异常: ${e.message}")
            }
        } catch (e: Exception) {
            LogManager.e(LogTags.VIDEO_DECODER, "解码帧失败: ${e.message}", e)
        }
    }

    /**
     * 🔥 优化的输出缓冲区处理 - 非阻塞，快速释放
     */
    private fun drainOutputBuffers(bufferInfo: MediaCodec.BufferInfo) {
        // ✅ 检查解码器状态
        if (isStopped) return
        
        try {
            val codec = decoder ?: return

            try {
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0) // 非阻塞

                while (outputIndex >= 0) {
                    // ✅ 关键：检查 Surface 状态，决定是否渲染
                    val shouldRender = synchronized(surfaceLock) {
                        surface != null && surface!!.isValid
                    }

                    // 后台时 shouldRender = false，丢弃帧但不崩溃
                    codec.releaseOutputBuffer(outputIndex, shouldRender)
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 输出格式变化，重新获取 crop 信息
                    LogManager.d(LogTags.VIDEO_DECODER, "🔄 输出格式变化")
                    updateVideoSizeFromOutputFormat(codec.outputFormat)
                }
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Uninitialized") == true ||
                    e.message?.contains("executing state") == true
                ) {
                    return
                }
                throw e
            }
        } catch (e: IllegalStateException) {
            // 解码器已释放，忽略
            if (!isStopped) {
                LogManager.w(LogTags.VIDEO_DECODER, "输出缓冲区处理异常: ${e.message}")
            }
        } catch (_: Exception) {
            // 忽略其他异常
        }
    }
}
