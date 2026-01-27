package com.mobile.scrcpy.android.feature.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mobile.scrcpy.android.common.ScrcpyConstants
import com.mobile.scrcpy.android.core.data.model.ScrcpySession
import com.mobile.scrcpy.android.core.data.model.SessionColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "sessions")

@Serializable
data class SessionData(
    val id: String,
    val name: String,
    val host: String,
    val port: String,
    val color: String,
    val forceAdb: Boolean = false,
    val maxSize: String = "",
    val bitrate: String = "",
    val maxFps: String = "",  // 最大帧率
    val videoCodec: String = ScrcpyConstants.DEFAULT_VIDEO_CODEC,
    val videoEncoder: String = "",
    val enableAudio: Boolean = false,
    val audioCodec: String = ScrcpyConstants.DEFAULT_AUDIO_CODEC,
    val audioEncoder: String = "",
    val audioBufferMs: String = "",
    val stayAwake: Boolean = false,  // 改为 false，不强制保持唤醒
    val turnScreenOff: Boolean = true,
    val powerOffOnClose: Boolean = false,
    // 编解码器缓存（避免每次启动都检测，仅在用户选择"默认"编码器时使用）
    val cachedVideoDecoder: String? = null,
    val cachedAudioDecoder: String? = null,
    val codecCacheTimestamp: Long = 0L,  // 缓存时间戳
    // 分组信息
    val groupIds: List<String> = emptyList()  // 所属分组 ID 列表，支持多分组
) {
    /**
     * 判断是否为 USB 连接
     */
    fun isUsbConnection(): Boolean {
        return host.startsWith("usb:")
    }
    
    /**
     * 获取 USB 序列号（仅 USB 模式有效）
     */
    fun getUsbSerialNumber(): String? {
        return if (isUsbConnection()) {
            host.removePrefix("usb:")
        } else {
            null
        }
    }
}

class SessionRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private object Keys {
        val SESSIONS = stringPreferencesKey("sessions_list")
    }

    val sessionsFlow: Flow<List<ScrcpySession>> = context.sessionDataStore.data.map { preferences ->
        val sessionsJson = preferences[Keys.SESSIONS] ?: "[]"
        try {
            val sessionDataList = json.decodeFromString<List<SessionData>>(sessionsJson)
            sessionDataList.map { it.toScrcpySession() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addSession(sessionData: SessionData) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SessionData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList + sessionData
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun removeSession(id: String) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SessionData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList.filter { it.id != id }
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun updateSession(sessionData: SessionData) {
        context.sessionDataStore.edit { preferences ->
            val currentJson = preferences[Keys.SESSIONS] ?: "[]"
            val currentList = try {
                json.decodeFromString<List<SessionData>>(currentJson)
            } catch (e: Exception) {
                emptyList()
            }
            val updatedList = currentList.map {
                if (it.id == sessionData.id) sessionData else it
            }
            preferences[Keys.SESSIONS] = json.encodeToString(updatedList)
        }
    }

    suspend fun getSessionData(id: String): SessionData? {
        val currentJson = context.sessionDataStore.data.map { preferences ->
            preferences[Keys.SESSIONS] ?: "[]"
        }.first()
        return try {
            json.decodeFromString<List<SessionData>>(currentJson).find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }

    val sessionDataFlow: Flow<List<SessionData>> = context.sessionDataStore.data.map { preferences ->
        val sessionsJson = preferences[Keys.SESSIONS] ?: "[]"
        try {
            json.decodeFromString<List<SessionData>>(sessionsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun SessionData.toScrcpySession() = ScrcpySession(
        id = id,
        name = name,
        color = SessionColor.valueOf(color),
        isConnected = false,
        hasWifi = host.isNotBlank(),
        hasWarning = false
    )
}
