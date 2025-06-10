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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun generateDefaultChatTitle(): String {
        val sdf = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
        return "新聊天 ${sdf.format(Date())}"
    }

    private fun String.smartTruncate(maxLength: Int): String {
        val trimmed = this.replace(Regex("\\s+"), " ").trim() // 替换多个空白为一个，并trim
        return if (trimmed.length > maxLength) {
            trimmed.substring(0, (maxLength - 3).coerceAtLeast(0)) + "..." // 确保索引不为负
        } else {
            trimmed
        }
    }

    fun suggestTitleFromMessages(messages: List<MessageData>, preferredMaxLength: Int = 30): String {
        // 优先尝试从用户的第一条非空消息中提取
        val firstUserMessageContent = messages.firstOrNull { it.role == "user" && it.content.isNotBlank() }?.content
        if (firstUserMessageContent != null) {
            // 尝试提取一些有意义的词汇
            val words = firstUserMessageContent
                .replace(Regex("\\p{Punct}+"), "") // 移除标点
                .split(Regex("\\s+")) // 按空白分割
                .filter { it.length > 1 } // 过滤掉太短的词

            if (words.isNotEmpty()) {
                val candidate = words.take(4).joinToString(" ") // 取前4个词
                if (candidate.length > 5) { // 确保候选标题有一定意义
                    return candidate.smartTruncate(preferredMaxLength)
                }
            }
            // 如果提取词汇效果不好，回退到直接截断原始消息
            return firstUserMessageContent.smartTruncate(preferredMaxLength)
        }

        // 如果没有用户消息，尝试从助手的第一条非空消息中提取（如果消息不是简单问候）
        val firstAssistantMessageContent = messages.firstOrNull {
            it.role == "assistant" && it.content.isNotBlank() &&
                    !it.content.matches(Regex("^(你好|您好|Hello|Hi).*$", RegexOption.IGNORE_CASE)) // 简单过滤问候语
        }?.content

        if (firstAssistantMessageContent != null) {
            return firstAssistantMessageContent.smartTruncate(preferredMaxLength)
        }

        // 如果都没有，返回基于时间的默认标题
        return generateDefaultChatTitle()
    }
}