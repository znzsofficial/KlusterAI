package com.nekolaska.klusterai

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import com.nekolaska.klusterai.data.DEFAULT_MODEL_API_NAME
import com.nekolaska.klusterai.data.MessageData
import com.nekolaska.klusterai.data.ModelSettings

@Serializable // 确保 MessageData 可序列化
data class MessageDataSerializable( // 创建一个可序列化的版本，如果原始 MessageData 不能直接序列化
    val role: String,
    val content: String,
    val thinkContent: String? = null,
    val id: Long
)

// MessageData 到 MessageDataSerializable 的转换函数
fun MessageData.toSerializable(): MessageDataSerializable {
    return MessageDataSerializable(this.role, this.content, this.thinkContent, this.id)
}

fun MessageDataSerializable.toMessageData(): MessageData {
    return MessageData(this.role, this.content, this.thinkContent, this.id)
}


@Serializable
data class SessionMeta(
    val id: String, // 唯一会话 ID
    var title: String, // 会话标题
    var lastModifiedTimestamp: Long, // 最后修改时间戳
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT, // 会话特定的系统提示
    var modelApiName: String = DEFAULT_MODEL_API_NAME, // 会话特定的模型
    var modelSettings: ModelSettings = ModelSettings.DEFAULT // 包含模型设置对象
)

object ChatSessionRepository {
    private const val SESSION_METADATA_PREFS = "chat_session_metadata_prefs"
    private const val SESSION_LIST_KEY = "session_list_v2" // 使用新key避免与旧数据冲突
    private const val CHAT_HISTORY_DIR = "chat_history"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true // 忽略未知键，增加向前兼容性
        isLenient = true // 更宽松的JSON解析
    }

    // --- 会话元数据操作 ---
    fun loadSessionMetas(context: Context): MutableList<SessionMeta> {
        val prefs = context.getSharedPreferences(SESSION_METADATA_PREFS, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(SESSION_LIST_KEY, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<List<SessionMeta>>(jsonString).toMutableList()
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveSessionMetas(context: Context, metas: List<SessionMeta>) {
        val prefs = context.getSharedPreferences(SESSION_METADATA_PREFS, Context.MODE_PRIVATE)
        val jsonString = json.encodeToString(metas)
        prefs.edit { putString(SESSION_LIST_KEY, jsonString) }
    }

    fun addOrUpdateSessionMeta(context: Context, meta: SessionMeta) {
        val metas = loadSessionMetas(context)
        val existingIndex = metas.indexOfFirst { it.id == meta.id }
        if (existingIndex != -1) {
            metas[existingIndex] = meta
        } else {
            metas.add(0, meta) // 新会话添加到顶部
        }
        metas.sortByDescending { it.lastModifiedTimestamp } // 确保按最后修改时间排序
        saveSessionMetas(context, metas)
    }

    fun getSessionMeta(context: Context, sessionId: String): SessionMeta? {
        return loadSessionMetas(context).find { it.id == sessionId }
    }

    fun deleteSessionMetaAndMessages(context: Context, sessionId: String) {
        val metas = loadSessionMetas(context)
        metas.removeAll { it.id == sessionId }
        saveSessionMetas(context, metas)
        deleteMessagesForSession(context, sessionId)
    }

    // --- 会话消息操作 ---
    fun getChatHistoryDir(context: Context): File {
        val dir = File(context.filesDir, CHAT_HISTORY_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun loadMessagesForSession(context: Context, sessionId: String): MutableList<MessageData> {
        val historyDir = getChatHistoryDir(context)
        val sessionFile = File(historyDir, "session_$sessionId.json")
        return if (sessionFile.exists()) {
            try {
                val jsonString = sessionFile.readText()
                json.decodeFromString<List<MessageDataSerializable>>(jsonString)
                    .map { it.toMessageData() } // 转换回 MessageData
                    .toMutableList()
            } catch (e: Exception) {
                e.printStackTrace()
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveMessagesForSession(context: Context, sessionId: String, messages: List<MessageData>) {
        val historyDir = getChatHistoryDir(context)
        val sessionFile = File(historyDir, "session_$sessionId.json")
        try {
            val serializableMessages = messages.map { it.toSerializable() } // 转换为可序列化版本
            val jsonString = json.encodeToString(serializableMessages)
            sessionFile.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            // Consider more robust error handling
        }
    }

    private fun deleteMessagesForSession(context: Context, sessionId: String) {
        val historyDir = getChatHistoryDir(context)
        val sessionFile = File(historyDir, "session_$sessionId.json")
        if (sessionFile.exists()) {
            sessionFile.delete()
        }
    }

    fun generateNewSessionId(): String = UUID.randomUUID().toString()

    fun suggestTitleFromMessages(messages: List<MessageData>): String {
        val firstUserMessage = messages.firstOrNull { it.role == "user" && it.content.isNotBlank() }
        return if (firstUserMessage != null) {
            firstUserMessage.content.take(30).let { if (it.length == 30) "$it..." else it }
        } else {
            "新聊天 ${System.currentTimeMillis() % 10000}" // 备用标题
        }
    }
}