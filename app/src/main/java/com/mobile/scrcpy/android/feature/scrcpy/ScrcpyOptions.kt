package com.mobile.scrcpy.android.feature.scrcpy

import com.mobile.scrcpy.android.common.ScrcpyConstants

data class ScrcpyOptions(
    val maxSize: Int = ScrcpyConstants.DEFAULT_MAX_SIZE,
    val bitRate: Int = ScrcpyConstants.DEFAULT_BITRATE_INT,
    val maxFps: Int = ScrcpyConstants.DEFAULT_MAX_FPS,
    val displayId: Int = ScrcpyConstants.DEFAULT_DISPLAY_ID,
    val showTouches: Boolean = false,
    val stayAwake: Boolean = true,
    val codecOptions: String = ScrcpyConstants.DEFAULT_CODEC_OPTIONS,
    val encoderName: String? = null,
    val powerOffOnClose: Boolean = false,
    val enableAudio: Boolean = false,
    val videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
    val audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
    val audioEncoder: String? = null,
    val audioBufferMs: Int? = null
) {
    fun toServerArgs(): List<String> {
        return buildList {
            add("log_level=info")
            add("max_size=$maxSize")
            add("video_bit_rate=$bitRate")
            add("max_fps=$maxFps")
            add("display_id=$displayId")
            add("show_touches=$showTouches")
            add("stay_awake=$stayAwake")
            add("video_codec_options=$codecOptions")
            add("tunnel_forward=true")
            add("video_codec=$videoCodec")
            if (encoderName != null) {
                add("video_encoder=$encoderName")
            }
            add("power_off_on_close=$powerOffOnClose")
            
            // 音频参数
            if (enableAudio) {
                add("audio_codec=$audioCodec")
                add("audio_bit_rate=${ScrcpyConstants.DEFAULT_AUDIO_BITRATE}")
                if (audioEncoder != null) {
                    add("audio_encoder=$audioEncoder")
                }
                if (audioBufferMs != null) {
                    add("audio_buffer=$audioBufferMs")
                }
            } else {
                add("audio=false")
            }
        }
    }
}
