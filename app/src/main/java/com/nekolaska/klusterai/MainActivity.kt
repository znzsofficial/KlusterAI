package com.nekolaska.klusterai

import android.content.ClipData
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nekolaska.klusterai.data.ApiRequestBody
import com.nekolaska.klusterai.data.ApiRequestMessage
import com.nekolaska.klusterai.data.ConfirmDialogState
import com.nekolaska.klusterai.data.DEFAULT_MODEL_API_NAME
import com.nekolaska.klusterai.data.MessageData
import com.nekolaska.klusterai.data.ModelSettings
import com.nekolaska.klusterai.data.VerificationResult
import com.nekolaska.klusterai.ui.components.AssistantLoadingIndicatorBar
import com.nekolaska.klusterai.ui.components.ConfirmActionDialog
import com.nekolaska.klusterai.ui.components.EditMessageDialog
import com.nekolaska.klusterai.ui.components.ErrorMessageDisplay
import com.nekolaska.klusterai.ui.components.InputRow
import com.nekolaska.klusterai.ui.components.MessageActionDialog
import com.nekolaska.klusterai.ui.components.MessageBubble
import com.nekolaska.klusterai.ui.components.SaveSessionDialog
import com.nekolaska.klusterai.ui.components.SessionListDialog
import com.nekolaska.klusterai.ui.components.SettingsDialog
import com.nekolaska.klusterai.ui.components.StreamingResponseDialog
import com.nekolaska.klusterai.ui.components.VerificationInProgressIndicator
import com.nekolaska.klusterai.ui.theme.KlusterAITheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KlusterAITheme {
                ChatScreen()
            }
        }
    }
}

// Json 实例，用于序列化请求体
// encodeDefaults = false: 如果属性值等于其在数据类中定义的默认值，则不序列化该属性。
// 对于可选参数，通常在数据类中将它们设为可空并默认值为 null。
// kotlinx.serialization 默认不序列化值为 null 的属性 (除非 explicitNulls = true)。
private val jsonRequestBuilder = Json {
    prettyPrint = false // API 请求通常不需要美化打印
    encodeDefaults = false // 重要：如果属性值等于其默认值，则不序列化。
    ignoreUnknownKeys = true // 解析响应时仍然有用（虽然这里主要用于序列化）
    isLenient = true         // 增加对不严格JSON格式的容忍度（对请求体影响较小）
}

// --- ChatScreen ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current // 获取生命周期所有者
    val coroutineScope = rememberCoroutineScope()
    var currentApiCallJob by remember { mutableStateOf<Job?>(null) } // 用于追踪当前的API调用Job

    var triggerRefresh by remember { mutableStateOf(false) } // 用于刷新会话列表
    // --- 全局状态 ---
    var autoSaveOnSwitchSessionGlobalPref by remember { //全局自动保存偏好
        mutableStateOf(SharedPreferencesUtils.loadAutoSaveOnSwitchPreference(context))
    }
    var autoVerifyResponseGlobalPref by remember { // 全局自动审查偏好
        mutableStateOf(SharedPreferencesUtils.loadAutoVerifyPreference(context))
    }
    var globalAutoShowStreamingDialogPref by remember { // 全局偏好
        mutableStateOf(SharedPreferencesUtils.loadGlobalAutoShowStreamingDialog(context))
    }
    var globalIsTextSelectablePref by remember { // 全局偏好
        mutableStateOf(SharedPreferencesUtils.loadGlobalIsTextSelectable(context))
    }

    var globalApiUrl by remember {
        mutableStateOf(SharedPreferencesUtils.loadApiUrl(context, DEFAULT_API_URL))
    }
    var globalApiKey by remember {
        mutableStateOf(SharedPreferencesUtils.loadApiKey(context, DEFAULT_API_KEY_FALLBACK))
    }
    var globalDefaultModelApiName by remember {
        mutableStateOf(SharedPreferencesUtils.loadSelectedModel(context, DEFAULT_MODEL_API_NAME))
    }
    var globalDefaultSystemPrompt by remember {
        mutableStateOf(SharedPreferencesUtils.loadSystemPrompt(context, DEFAULT_SYSTEM_PROMPT))
    }
    var globalDefaultModelSettings by remember { // 单个对象存储全局模型设置
        mutableStateOf(SharedPreferencesUtils.loadGlobalModelSettings(context))
    }

    // --- 当前会话相关状态 ---
    var currentSessionId by remember { mutableStateOf<String?>(null) } // 当前打开的会话ID，null表示新聊天（未保存）
    var currentSessionTitleInput by remember { mutableStateOf(TextFieldValue("新聊天")) } // 用于保存对话框的标题输入
    val conversationHistory = remember { mutableStateListOf<MessageData>() }
    var isCurrentChatModified by remember { mutableStateOf(false) } // 当前聊天自上次保存以来是否有修改

    // 当前激活的会话设置 (从SessionMeta加载，或使用全局默认)
    var activeModelApiName by remember { mutableStateOf(globalDefaultModelApiName) }
    var activeSystemPrompt by remember { mutableStateOf(globalDefaultSystemPrompt) }
    var activeModelSettings by remember { mutableStateOf(globalDefaultModelSettings) } // 单个对象存储当前会话激活的模型设置

    // --- 幻觉审查相关状态 ---
    var lastAssistantMessageIdForVerification by remember { mutableStateOf<Long?>(null) } // 用于关联审查结果和消息
    var verificationResultForLastMessage by remember { mutableStateOf<VerificationResult?>(null) }
    var showVerificationInProgress by remember { mutableStateOf(false) } // 显示审查进行中的状态

    // --- UI 控制状态 ---
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()


    var showSettingsDialog by remember { mutableStateOf(false) }
    var showActionMenuDialog by remember { mutableStateOf(false) }
    var messageForAction by remember { mutableStateOf<MessageData?>(null) }
    var showStreamingDialog by remember { mutableStateOf(false) }
    val streamingContent = remember { mutableStateOf("") }
    var showSaveSessionDialog by remember { mutableStateOf(false) }
    var showSessionListDialog by remember { mutableStateOf(false) }

    // 编辑消息对话框
    var messageToEditState by remember { mutableStateOf<MessageData?>(null) }
    var showEditMessageDialog by remember { mutableStateOf(false) }

    // 用于通用确认对话框的状态
    var confirmDialogState by remember { mutableStateOf<ConfirmDialogState?>(null) }

    val allSessionMetas = remember { mutableStateListOf<SessionMeta>() }


    // --- 辅助函数 ---
    fun markAsModified(modified: Boolean = true) {
        isCurrentChatModified = modified
    }

    fun updateActiveSessionSettings(meta: SessionMeta?) {
        if (meta != null) {
            activeModelApiName = meta.modelApiName
            activeSystemPrompt = meta.systemPrompt
            activeModelSettings = meta.modelSettings // 加载会话的模型设置
        } else { // 新聊天或无会话数据时，使用全局默认
            activeModelApiName = globalDefaultModelApiName
            activeSystemPrompt = globalDefaultSystemPrompt
            activeModelSettings = globalDefaultModelSettings // 新聊天使用全局默认模型设置
        }
        updateSystemMessageInHistory(conversationHistory, activeSystemPrompt)
    }

    fun loadSession(sessionId: String) {
        val meta = ChatSessionRepository.getSessionMeta(context, sessionId)
        if (meta != null) {
            currentSessionId = meta.id
            currentSessionTitleInput = TextFieldValue(meta.title) // 更新标题输入状态
            conversationHistory.clear()
            conversationHistory.addAll(
                ChatSessionRepository.loadMessagesForSession(
                    context,
                    sessionId
                )
            )
            updateActiveSessionSettings(meta)
            markAsModified(false) // 加载后是未修改状态
            coroutineScope.launch { listState.scrollToItem(0) } // 滚动到顶部或最新消息
            Toast.makeText(context, "已加载: ${meta.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "加载会话失败: $sessionId", Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSaveCurrentChatAndThen(title: String, andThenAction: (() -> Unit)? = null) {
        val sessionIdToSave = currentSessionId ?: ChatSessionRepository.generateNewSessionId()
        val newMeta = SessionMeta(
            id = sessionIdToSave,
            title = title,
            lastModifiedTimestamp = System.currentTimeMillis(),
            systemPrompt = activeSystemPrompt,
            modelApiName = activeModelApiName,
            modelSettings = activeModelSettings
        )
        ChatSessionRepository.addOrUpdateSessionMeta(context, newMeta)
        ChatSessionRepository.saveMessagesForSession(
            context,
            sessionIdToSave,
            conversationHistory.toList()
        )

        // 更新内存中的会话列表
        val existingMetaIndex = allSessionMetas.indexOfFirst { it.id == sessionIdToSave }
        if (existingMetaIndex != -1) {
            allSessionMetas[existingMetaIndex] = newMeta
        } else {
            allSessionMetas.add(0, newMeta)
        }
        allSessionMetas.sortByDescending { it.lastModifiedTimestamp }

        currentSessionId = sessionIdToSave // 标记当前聊天为已保存的这个ID
        currentSessionTitleInput = TextFieldValue(title) // 更新UI上的标题
        markAsModified(false)
        Toast.makeText(context, "会话已自动保存: $title", Toast.LENGTH_SHORT).show()
        showSaveSessionDialog = false // 关闭可能的保存对话框
        andThenAction?.invoke() // 执行后续操作
    }

    fun prepareNewChat() {
        currentSessionId = null
        currentSessionTitleInput =
            TextFieldValue(ChatSessionRepository.suggestTitleFromMessages(emptyList())) // 建议一个新标题
        conversationHistory.clear()
        updateActiveSessionSettings(null) // 使用全局默认
        userInput = ""
        errorMessage = null
        isLoading = false
        markAsModified(false) // 新聊天初始未修改
        coroutineScope.launch { if (conversationHistory.isNotEmpty()) listState.scrollToItem(0) }
    }

    fun startNewChatConfirmed() {
        prepareNewChat()
        Toast.makeText(context, "新聊天已开始", Toast.LENGTH_SHORT).show()
    }

    fun tryStartingNewChat() {
        val action = { startNewChatConfirmed() }

        if (isCurrentChatModified) {
            if (autoSaveOnSwitchSessionGlobalPref) { // 检查自动保存偏好
                val suggestedTitle = currentSessionTitleInput.text.ifBlank {
                    ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
                }
                handleSaveCurrentChatAndThen(suggestedTitle, action) // 保存并执行后续操作
            } else {
                confirmDialogState = ConfirmDialogState(
                    title = "未保存的更改",
                    text = "当前聊天有未保存的更改。开始新聊天将丢失这些更改。确定要继续吗？\n（您可以在设置中开启切换时自动保存）",
                    onConfirm = action
                )
            }
        } else {
            action()
        }
    }

    fun tryLoadingSession(sessionId: String) {
        if (currentSessionId == sessionId) return

        val action = {
            loadSession(sessionId)
            showSessionListDialog = false
        }

        if (isCurrentChatModified) {
            if (autoSaveOnSwitchSessionGlobalPref) { // 检查自动保存偏好
                val suggestedTitle = currentSessionTitleInput.text.ifBlank {
                    ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
                }
                handleSaveCurrentChatAndThen(suggestedTitle, action) // 保存并执行后续操作
            } else {
                confirmDialogState = ConfirmDialogState(
                    title = "未保存的更改",
                    text = "切换会话前是否保存当前聊天？若不保存，更改将丢失。\n（您可以在设置中开启切换时自动保存）",
                    onConfirm = action // 用户确认丢失更改
                )
            }
        } else {
            action()
        }
    }

    fun handleDeleteSession(sessionId: String) {
        val sessionToDelete = allSessionMetas.find { it.id == sessionId } ?: return
        confirmDialogState = ConfirmDialogState(
            title = "删除会话",
            text = "确定要删除会话 “${sessionToDelete.title}” 吗？此操作不可撤销。",
            onConfirm = {
                ChatSessionRepository.deleteSessionMetaAndMessages(context, sessionId)
                allSessionMetas.removeAll { it.id == sessionId }
                if (currentSessionId == sessionId) { // 如果删除的是当前会话
                    prepareNewChat() // 开始一个新聊天
                }
                Toast.makeText(
                    context,
                    "会话 “${sessionToDelete.title}” 已删除",
                    Toast.LENGTH_SHORT
                ).show()
                showSessionListDialog = allSessionMetas.isNotEmpty() // 如果列表空了就关闭
            }
        )
    }

    fun handleRenameSession(sessionId: String, newTitle: String) {
        val meta = allSessionMetas.find { it.id == sessionId }
        meta?.let {
            it.title = newTitle
            it.lastModifiedTimestamp = System.currentTimeMillis()
            ChatSessionRepository.addOrUpdateSessionMeta(context, it)
            if (sessionId == currentSessionId) {
                currentSessionTitleInput = TextFieldValue(newTitle)
            }
            allSessionMetas.sortByDescending { s -> s.lastModifiedTimestamp } // 重新排序
            Toast.makeText(context, "标题已更新为: $newTitle", Toast.LENGTH_SHORT).show()
        }
    }


    // --- 初始化加载 ---
    LaunchedEffect(Unit, triggerRefresh) { // 当 triggerRefresh 变化时重新加载
        //Log.d("ChatScreen", "Refreshing session metas due to Unit or triggerRefresh change.")
        allSessionMetas.clear()
        allSessionMetas.addAll(ChatSessionRepository.loadSessionMetas(context))
        if (allSessionMetas.isNotEmpty() && currentSessionId == null) {
            loadSession(allSessionMetas.first().id)
        } else if (currentSessionId != null && allSessionMetas.none { it.id == currentSessionId }) {
            prepareNewChat()
        } else if (currentSessionId != null) {
            // 如果当前会话ID仍然有效，确保其信息是最新的（例如标题可能在导入时被修改为副本）
            // loadSession(currentSessionId!!) // 可能会导致不必要的重新加载，除非确保数据已变
            // 更好的方式是直接从 allSessionMetas 更新 currentSessionTitleInput
            allSessionMetas.find { it.id == currentSessionId }?.let {
                currentSessionTitleInput = TextFieldValue(it.title)
            }
        }
        //Log.d("ChatScreen", "Session metas refreshed. Count: ${allSessionMetas.size}")
    }

    // --- 生命周期事件处理：退出时自动保存 ---
    DisposableEffect(
        lifecycleOwner,
        isCurrentChatModified,
        autoSaveOnSwitchSessionGlobalPref,
        currentSessionId,
        currentSessionTitleInput.text
    ) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { // 或者 ON_PAUSE，但 ON_STOP 更接近“退出”
                if (isCurrentChatModified && autoSaveOnSwitchSessionGlobalPref) {
                    // 确定保存标题
                    val titleToSaveOnExit = if (currentSessionId != null) {
                        currentSessionTitleInput.text // 如果是已保存的会话，使用当前标题
                    } else {
                        // 如果是新聊天，尝试使用用户可能在标题输入框中输入的值，否则建议一个
                        currentSessionTitleInput.text.ifBlank {
                            ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
                        }
                    }
                    // 确保标题不为空
                    val finalTitle = titleToSaveOnExit.ifBlank {
                        ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
                    }

                    // 调用保存逻辑，但不进行后续操作 (andThenAction = null)
                    // 注意：这里的 context 可能需要从外部传入，或者确保 handleSaveCurrentChatAndThen 能正确访问
                    // 但由于 handleSaveCurrentChatAndThen 已经是 ChatScreen 内部函数，它可以访问 context
                    handleSaveCurrentChatAndThen(finalTitle, null) // 保存当前状态
                    //Log.d("ChatScreenLifecycle", "Attempting auto-save on stop.")
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            //Log.d("ChatScreenLifecycle", "Observer removed.")
        }
    }


    // --- 幻觉审查逻辑 ---
    fun processAndDisplayFinalResponse(
        assistantMessage: MessageData,
        verificationResult: VerificationResult?,
        insertionIndex: Int?
    ) {
        //Log.d("VerificationProcess", "Processing final response. Hallucination: ${verificationResult?.hallucination}")
        val finalMessageToShow = assistantMessage // 默认显示原始消息

        // 更新状态以供 MessageBubble 使用
        lastAssistantMessageIdForVerification = assistantMessage.id
        verificationResultForLastMessage = verificationResult

        if (verificationResult != null && verificationResult.hallucination != "0") {
            //Log.w("Verification", "幻觉检测到: ${verificationResult.reasoning}")
            Toast.makeText(context, "注意: 回复可能包含不准确之处。", Toast.LENGTH_LONG).show()
            // MessageBubble 会根据 verificationResultForLastMessage 和消息ID来显示提示
        }

        val existingIndex = conversationHistory.indexOfFirst { it.id == finalMessageToShow.id }
        if (existingIndex != -1) { // 如果草稿已存在（例如，在流式预览中部分添加）
            conversationHistory[existingIndex] = finalMessageToShow
        } else if (insertionIndex != null && insertionIndex >= 0 && insertionIndex <= conversationHistory.size) {
            conversationHistory.add(insertionIndex, finalMessageToShow)
        } else {
            conversationHistory.add(finalMessageToShow)
        }
        markAsModified() // 最终消息添加到历史就算修改
        // 清理 lastAssistantMessageIdForVerification 和 verificationResultForLastMessage 由 MessageBubble 的 key 变化或下一个审查触发
    }

    fun triggerVerification(
        contextForVerification: List<MessageData>, // 包含主模型回复的完整上下文
        originalAssistantMessage: MessageData, // 主模型刚生成的回复
        insertionIndexForOriginal: Int? // 原计划插入主模型回复的位置
    ) {
        if (globalApiKey.isBlank()) {
            errorMessage = "API 密钥为空，无法进行审查。"
            showVerificationInProgress = false
            processAndDisplayFinalResponse(
                originalAssistantMessage,
                null,
                insertionIndexForOriginal
            )
            return
        }
        showVerificationInProgress = true
        verificationResultForLastMessage = null // 清除上一次的特定审查结果

        val verificationMessages = contextForVerification.toMutableList() // 已经是完整的了
        verificationMessages.add(MessageData(role = "user", content = VERIFICATION_USER_PROMPT))

        //Log.d("Verification", "Triggering verification for message ID: ${originalAssistantMessage.id}")

        coroutineScope.launch {
            var completeJsonResponseFromApi: String?
            try {
                val verificationSettings = ModelSettings.DEFAULT
//                    .copy(
//                    temperature = 0.1f,
//                    topP = 0.1f,
//                )

                completeJsonResponseFromApi = callLLMApi(
                    apiUrl = globalApiUrl,
                    apiKey = globalApiKey,
                    modelApiName = VERIFICATION_MODEL_NAME,
                    currentHistory = verificationMessages, // 包含审查指令
                    settings = verificationSettings,
                    onChunkReceived = { chunk ->
                        // 假设审查模型将整个 JSON 作为 content 返回，这个 chunk 就是它
                        //Log.v("VerificationChunk", "Chunk: $chunk")
                        // 对于非流式JSON，这个回调可能只被调用一次，内容是完整的JSON
                    }
                )
                //Log.d("Verification", "Verification raw JSON response: $completeJsonResponseFromApi")

                if (completeJsonResponseFromApi != null && completeJsonResponseFromApi.isNotBlank()) {
                    try {
                        val result = jsonParser.decodeFromString<VerificationResult>(
                            completeJsonResponseFromApi
                        )
                        processAndDisplayFinalResponse(
                            originalAssistantMessage,
                            result,
                            insertionIndexForOriginal
                        )
                    } catch (_: SerializationException) {
                        errorMessage = "解析审查结果失败: JSON格式错误。"
                        //Log.e("Verification", "JSON Parsing failed for: $completeJsonResponseFromApi", e)

                        // 凑活着显示
                        processAndDisplayFinalResponse(
                            originalAssistantMessage,
                            VerificationResult(completeJsonResponseFromApi, "1"),
                            insertionIndexForOriginal
                        )
                    }
                } else {
                    errorMessage = "审查模型返回空响应。"
                    //Log.e("Verification", "Verification model returned null or blank.")
                    processAndDisplayFinalResponse(
                        originalAssistantMessage,
                        null,
                        insertionIndexForOriginal
                    )
                }
            } catch (e: Exception) {
                errorMessage = "幻觉审查 API 调用失败: ${e.message}"
                //Log.e("Verification", "Verification API call failed", e)
                processAndDisplayFinalResponse(
                    originalAssistantMessage,
                    null,
                    insertionIndexForOriginal
                )
            } finally {
                showVerificationInProgress = false
            }
        }
    }

    fun handlePartialOrInterruptedResponse(isCancelledByUser: Boolean = false) {
        isLoading = false // 确保加载状态被重置
        // showStreamingDialog = false; // 对话框可能已经被关闭或将被关闭

        if (streamingContent.value.isNotBlank()) {
            val (thinkPart, remainingPart) = extractThinkSection(streamingContent.value)
            var contentToSave = remainingPart

            val note: String = if (isCancelledByUser) {
                "\n(用户手动中断)"
            } else { // 其他原因的中断，例如网络错误或后台
                "\n(回复可能不完整或因连接问题中断)"
            }

            if (remainingPart.isBlank() && !isCancelledByUser) { // 如果是空白且非用户中断，可能没必要保存
                //Log.d("ChatScreen", "响应中断，但已接收内容为空白，不添加到历史。")
                streamingContent.value = "" // 清空
                return
            }

            contentToSave += note

            val partialMessage = MessageData(
                role = "assistant",
                content = contentToSave,
                thinkContent = thinkPart
            )
            // 决定插入位置，通常是追加
            // 注意：此时 assistantMessageDraft 可能为 null，我们使用 streamingContent.value
            processAndDisplayFinalResponse(
                partialMessage,
                null,
                null
            ) // 使用 null 作为 insertionIndex 来追加
            markAsModified() // 标记有更改
            Toast.makeText(context, "回复已中断，部分内容可能已保存。", Toast.LENGTH_LONG).show()
        } else if (isCancelledByUser) { // 用户手动中断但没有收到任何内容
            Toast.makeText(context, "回复已中断。", Toast.LENGTH_SHORT).show()
        }
        streamingContent.value = "" // 确保清空
    }

    // interruptCurrentStream 函数现在可以调用 handlePartialOrInterruptedResponse
    fun interruptCurrentStream() {
        currentApiCallJob?.cancel("User interrupted stream")
        handlePartialOrInterruptedResponse(isCancelledByUser = true) // 明确是用户中断
    }

    fun triggerLLMRequest(historyForRequest: List<MessageData>, insertionIndex: Int? = null) {
        if (globalApiKey.isBlank()) {
            errorMessage = "API 密钥为空"; return
        }
        // 确保 System Prompt 在请求历史中是正确的
        val actualHistory = historyForRequest.toMutableList()
        updateSystemMessageInHistory(actualHistory, activeSystemPrompt) // 确保使用当前激活的系统提示

        if (actualHistory.filterNot { it.role == "system" && it.content.isBlank() }
                .none { it.role == "user" && it.content.isNotBlank() } && actualHistory.none { it.role == "assistant" }) { // 确保至少有一个非空用户消息或助手消息（允许仅系统提示开始）
            if (actualHistory.all { it.role == "system" } && actualHistory.any { it.content.isNotBlank() }) {
                // 如果只有非空系统提示，也允许（某些模型支持）
            } else {
                errorMessage = "请输入有效消息后再发送。"
                return
            }
        }

        errorMessage = null
        isLoading = true
        streamingContent.value = ""
        // 根据设置决定是否立即显示流式对话框
        showStreamingDialog = globalAutoShowStreamingDialogPref
        // 清理上一次的审查状态，因为我们要获取新回复
        lastAssistantMessageIdForVerification = null
        verificationResultForLastMessage = null

        // 取消之前可能正在进行的API调用
        currentApiCallJob?.cancel("New request started")

        currentApiCallJob = coroutineScope.launch {
            var fullResponse: String?
            var assistantMessageDraft: MessageData? = null // 发送给审查模型的当前回复
            var operationCompletedSuccessfully = false // 标记操作是否正常完成
            try {
                fullResponse = callLLMApi(
                    apiUrl = globalApiUrl,
                    apiKey = globalApiKey,
                    modelApiName = activeModelApiName,
                    currentHistory = actualHistory,
                    settings = activeModelSettings,
                    onChunkReceived = { chunk ->
                        if (isActive) { // 检查协程是否仍然活动
                            streamingContent.value += chunk
                        }
                    }
                )

                if (isActive) { // 仅当协程未被取消时处理结果
                    if (fullResponse != null && fullResponse.isNotBlank()) {
                        val (thinkPart, remainingPart) = extractThinkSection(fullResponse)
                        assistantMessageDraft = MessageData(
                            role = "assistant",
                            content = remainingPart,
                            thinkContent = thinkPart
                        )
                        operationCompletedSuccessfully = true
                    } else if (streamingContent.value.isNotBlank() && fullResponse.isNullOrBlank()) {
                        val (thinkPart, remainingPart) = extractThinkSection(streamingContent.value)
                        assistantMessageDraft = MessageData(
                            role = "assistant",
                            content = remainingPart,
                            thinkContent = thinkPart
                        )
                        operationCompletedSuccessfully = true // 视作一种完成，即使 fullResponse 为空
                    } else {
                        if (isActive) errorMessage = "从API收到空响应或无效响应。"
                        // operationCompletedSuccessfully 保持 false
                    }
                }
            } catch (e: IOException) {
                if (isActive) errorMessage = "API 调用错误 (网络问题?): ${e.message}"
            } catch (e: Exception) {
                if (isActive && e !is CancellationException) { // 不要把正常的取消当作错误处理
                    errorMessage = "发生未知错误: ${e.message}"; e.printStackTrace()
                    // 如果是因为中断而取消，streamingContent 可能已有部分内容，interruptCurrentStream 会处理
                    // 如果是新请求覆盖旧请求而取消，则 streamingContent 应该在下一次请求开始时清空
                }
            } finally {
                // `isActive` 在 finally 块中可能不再可靠，因为协程可能正在完成其清理工作。
                // 我们通过 `operationCompletedSuccessfully` 和 `e is CancellationException` 来判断。

                isLoading = false // 总是在 finally 中重置 isLoading

                if (operationCompletedSuccessfully && assistantMessageDraft != null) {
                    // 正常完成，走审查流程或直接显示
                    if (autoVerifyResponseGlobalPref) {
                        val contextForVerification =
                            actualHistory.toMutableList().apply { add(assistantMessageDraft) }
                        lastAssistantMessageIdForVerification = assistantMessageDraft.id
                        triggerVerification(
                            contextForVerification,
                            assistantMessageDraft,
                            insertionIndex
                        )
                    } else {
                        processAndDisplayFinalResponse(assistantMessageDraft, null, insertionIndex)
                    }
                } else if (currentApiCallJob?.isCancelled == false && streamingContent.value.isNotBlank()) {
                    // 非用户取消（例如网络错误）但收到了一些内容
                    //Log.d("LLMRequest", "Operation failed but some content was streamed. Handling partial.")
                    handlePartialOrInterruptedResponse(isCancelledByUser = false)
                } else if (currentApiCallJob?.isCancelled == true && streamingContent.value.isNotBlank()) {
                    // 被取消（可能是用户手动中断，或新请求覆盖，或后台）且有部分内容
                    // 如果是用户手动中断，interruptCurrentStream 已经调用了 handlePartialOrInterruptedResponse
                    // 如果是新请求覆盖旧请求，我们可能不希望保存旧请求的部分内容，因为 streamingContent.value 在新请求开始时会被清空。
                    // 如果是后台等原因，可以考虑保存。
                    // 为了避免重复处理用户手动中断，这里可以加个判断。
                    // 但由于 interruptCurrentStream 内部会 cancel job，这里的 isCancelled 会为 true。
                    // 一个更简单的方法是：如果 operationCompletedSuccessfully 是 false，并且 streamingContent 有值，
                    // 并且我们没有明确地知道这是用户通过中断按钮触发的（这种情况由 interruptCurrentStream 单独处理），
                    // 那么我们可以认为这是一个“意外”中断，并尝试保存。
                    // 但 `interruptCurrentStream` 也会设置 isCancelled = true.
                    // 所以，这里的逻辑是：如果不是成功完成，也不是用户通过中断按钮（因为那个有自己的Toast和处理），
                    // 但是streamingContent有内容，那就可能是其他原因的中断。
                    // **一个更简洁的做法是在 interruptCurrentStream 之外的取消（如后台、网络）都走这里的逻辑。**
                    //Log.d("LLMRequest", "Job was cancelled and some content was streamed. Handling partial if not user-invoked interruption.")
                    // 为了避免与 interruptCurrentStream 中的 handlePartialOrInterruptedResponse 重复调用，
                    // 我们可以在 interruptCurrentStream 中加一个标记，或者让它不直接调用 handlePartial...
                    // 暂时我们先这样，如果 interruptCurrentStream 先调用，它会清空 streamingContent.value。
                    // 如果是其他原因的 CancellationException，这里可以捕获。
                    handlePartialOrInterruptedResponse(isCancelledByUser = false) // 假设非用户手动按钮触发的中断
                } else {
                    // 失败且没有流式内容（或者流式内容为空）
                    //Log.e("LLMRequest", "主LLM未能生成有效回复或流式内容为空。")
                    if (errorMessage == null && isActive) { // 如果没有特定错误信息，给一个通用提示
                        errorMessage = "未能获取回复。" // 这个可能过于笼统
                    }
                }
            }
        }
    }


    // --- 返回键处理 ---
    BackHandler(enabled = isCurrentChatModified) { // 仅当有修改时启用
        if (autoSaveOnSwitchSessionGlobalPref) {
            val titleToSaveOnBack = currentSessionTitleInput.text.ifBlank {
                ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
            }
            val finalTitle = titleToSaveOnBack.ifBlank {
                ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList())
            }
            handleSaveCurrentChatAndThen(finalTitle) {
                // 自动保存后，允许退出或执行其他返回操作
                //Log.d("ChatScreenBackHandler", "Auto-saved on back press, allowing exit.")
                // 如果这是应用的最后一个 Activity，可能需要 (context as? ComponentActivity)?.finish()
                // 如果有导航栈，则是 navController.popBackStack()
                // 这里我们假设按返回键就是想退出当前屏幕/应用
                (context as? ComponentActivity)?.finishAffinity() // 关闭应用
            }
        } else {
            confirmDialogState = ConfirmDialogState(
                title = "未保存的更改",
                text = "当前聊天有未保存的更改。确定要退出吗？",
                onConfirm = {
                    (context as? ComponentActivity)?.finishAffinity() // 确认后退出应用
                }
            )
        }
    }

    // --- UI ---
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentSessionTitleInput.text + if (isCurrentChatModified) "*" else "", // 标题后加星号表示未保存
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            // 点击标题允许重命名（如果已保存）或触发保存（如果未保存）
                            if (currentSessionId != null) {
                                showSessionListDialog = true // 通过会话列表重命名
                            } else {
                                currentSessionTitleInput = TextFieldValue(
                                    ChatSessionRepository.suggestTitleFromMessages(
                                        conversationHistory.toList()
                                    ),
                                    selection = TextRange(
                                        ChatSessionRepository.suggestTitleFromMessages(
                                            conversationHistory.toList()
                                        ).length
                                    )
                                )
                                showSaveSessionDialog = true
                            }
                        }
                    )
                },
                navigationIcon = { // 汉堡菜单图标用于打开会话列表
                    IconButton(onClick = { showSessionListDialog = true }) {
                        Icon(Icons.Filled.Menu, contentDescription = "会话列表")
                    }
                },
                actions = {
                    IconButton(onClick = { // 保存当前会话
                        currentSessionTitleInput = TextFieldValue( // 预填标题
                            if (currentSessionId != null) allSessionMetas.find { it.id == currentSessionId }?.title
                                ?: ChatSessionRepository.suggestTitleFromMessages(
                                    conversationHistory.toList()
                                )
                            else ChatSessionRepository.suggestTitleFromMessages(conversationHistory.toList()),
                            selection = TextRange(
                                (if (currentSessionId != null) allSessionMetas.find { it.id == currentSessionId }?.title
                                    ?: ChatSessionRepository.suggestTitleFromMessages(
                                        conversationHistory.toList()
                                    ) else ChatSessionRepository.suggestTitleFromMessages(
                                    conversationHistory.toList()
                                )).length
                            )
                        )
                        showSaveSessionDialog = true
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.save),
                            contentDescription = "保存会话"
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "打开设置")
                    }
                    IconButton(onClick = { tryStartingNewChat() }) {
                        Icon(Icons.Filled.Add, contentDescription = "新建聊天")
                    }
                }
            )
        },
        bottomBar = {
            Column {
                if (isLoading && !showStreamingDialog) {
                    AssistantLoadingIndicatorBar { showStreamingDialog = true }
                }
                InputRow(
                    userInput = userInput,
                    onUserInputChange = { newUserInput -> userInput = newUserInput },
                    onSendClick = {
                        if (userInput.isNotBlank() && !isLoading) {
                            val userMessageData = MessageData(role = "user", content = userInput)
                            conversationHistory.add(userMessageData)
                            markAsModified()
                            val historyForApiCall = conversationHistory.toList()
                            userInput = "" // 清空输入框
                            // isInputExpanded = false // 可选：发送后自动折叠输入框
                            triggerLLMRequest(historyForApiCall)
                        }
                    },
                    isLoading = isLoading,
                    apiKey = globalApiKey
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 审查进行中提示条
            if (showVerificationInProgress) VerificationInProgressIndicator()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false // 正常顺序
            ) {
                items(conversationHistory, key = { it.id }) { message ->
                    // 获取这条消息对应的审查结果
                    val currentVerification =
                        if (message.id == lastAssistantMessageIdForVerification) {
                            verificationResultForLastMessage
                        } else {
                            null
                        }
                    MessageBubble(
                        message = message,
                        isContentSelectable = globalIsTextSelectablePref,
                        verificationResult = if (message.role == "assistant") currentVerification else null,
                        onCopyFeedbackAndEdit = { _, feedback -> // originalQuery暂时为空，下面处理
                            // 找到这条助手消息之前的最近一条用户消息
                            val assistantMessageIndex =
                                conversationHistory.indexOfFirst { it.id == message.id }
                            var originalUserQuery = "我不记得之前问了什么，" // 默认值

                            if (assistantMessageIndex > 0) {
                                // 从助手消息往前找最近的用户消息
                                for (i in assistantMessageIndex - 1 downTo 0) {
                                    if (conversationHistory[i].role == "user") {
                                        originalUserQuery = conversationHistory[i].content
                                        break
                                    }
                                }
                            }

                            val newPrompt =
                                "根据以下反馈： “${feedback.take(300)}”\n请重新考虑或修正你对这个问题的回答：“${
                                    originalUserQuery.take(300)
                                }”\n请直接给出修正后的回答："
                            userInput = newPrompt // 将构造好的提示填充到输入框
                            // 可以在这里加一个 Toast 提示用户输入框已填充
                            Toast.makeText(
                                context,
                                "反馈已填充到输入框，请修改后发送",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    ) { msg ->
                        messageForAction = msg
                        showActionMenuDialog = true
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            LaunchedEffect(conversationHistory.size) {
                if (conversationHistory.isNotEmpty()) {
                    listState.animateScrollToItem(conversationHistory.size - 1) // 滚动到最新消息
                }
            }
            // 错误消息
            ErrorMessageDisplay(
                errorMessage = errorMessage,
                onDismiss = { errorMessage = null }, // 当用户关闭时，将 errorMessage 状态设为 null
                modifier = Modifier.padding(horizontal = 16.dp) // 给错误组件设置左右边距，使其与聊天内容对齐
            )
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            globalApiKey = globalApiKey,
            globalApiUrl = globalApiUrl,
            globalDefaultModelApiName = globalDefaultModelApiName,
            globalDefaultSystemPrompt = globalDefaultSystemPrompt,
            globalDefaultModelSettings = globalDefaultModelSettings,
            globalAutoSaveOnSwitch = autoSaveOnSwitchSessionGlobalPref,
            globalAutoVerifyResponse = autoVerifyResponseGlobalPref,
            globalAutoShowStreamingDialogPref = globalAutoShowStreamingDialogPref,
            globalIsTextSelectablePref = globalIsTextSelectablePref,

            currentSessionModelApiName = if (currentSessionId != null) activeModelApiName else null,
            currentSessionSystemPrompt = if (currentSessionId != null) activeSystemPrompt else null,
            currentSessionModelSettings = if (currentSessionId != null) activeModelSettings else null,

            onSaveGlobalDefaults = { autoSave, autoVerify, autoShow, selectable, apiKey, apiUrl, modelName, prompt, settings ->
                // 更新 ChatScreen 中的全局状态
                autoSaveOnSwitchSessionGlobalPref = autoSave
                autoVerifyResponseGlobalPref = autoVerify
                globalAutoShowStreamingDialogPref = autoShow
                globalIsTextSelectablePref = selectable
                globalApiKey = apiKey
                globalApiUrl = apiUrl
                globalDefaultModelApiName = modelName
                globalDefaultSystemPrompt = prompt
                globalDefaultModelSettings = settings

                // 持久化到 SharedPreferences
                SharedPreferencesUtils.apply {
                    saveAutoSaveOnSwitchPreference(context, autoSave)
                    saveAutoVerifyPreference(context, autoVerify)
                    saveGlobalAutoShowStreamingDialog(context, autoShow)
                    saveGlobalIsTextSelectable(context, selectable)
                    saveApiKey(context, apiKey)
                    saveApiUrl(context, apiUrl)
                    saveSelectedModel(context, modelName)
                    saveSystemPrompt(context, prompt)
                    saveGlobalModelSettings(context, settings)
                }

                // 如果当前没有活动会话，或者当前活动会话正在使用全局设置，则需要更新 activeXXX 状态
                if (currentSessionId == null || (
                            activeModelApiName == (allSessionMetas.find { it.id == currentSessionId }?.modelApiName
                                ?: globalDefaultModelApiName) && // 检查是否之前用了全局的
                                    activeSystemPrompt == (allSessionMetas.find { it.id == currentSessionId }?.systemPrompt
                                ?: globalDefaultSystemPrompt) &&
                                    activeModelSettings == (allSessionMetas.find { it.id == currentSessionId }?.modelSettings
                                ?: globalDefaultModelSettings)
                            )
                ) {
                    updateActiveSessionSettings(null) // 这会使 activeXXX 更新为新的全局默认值
                }
                Toast.makeText(context, "全局默认设置已保存", Toast.LENGTH_SHORT).show()
            },
            onUpdateCurrentSessionSettings = { modelName, prompt, settings ->
                // 当用户在“当前会话”页更改设置时，立即更新 ChatScreen 的 active 状态
                // 并标记会话已修改，等待用户在 ChatScreen 点击“保存会话”来持久化
                if (currentSessionId != null) {
                    activeModelApiName = modelName
                    activeSystemPrompt = prompt
                    activeModelSettings = settings
                    updateSystemMessageInHistory(conversationHistory, prompt) // 更新聊天记录中的系统消息
                    markAsModified()
                    //Toast.makeText(context, "当前会话设置已更改 (待保存)", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showSettingsDialog = false },
            hasActiveSession = currentSessionId != null,
            onChatHistoryImportedParent = {
                triggerRefresh = !triggerRefresh // 触发会话列表刷新
                // onDismiss() // 导入成功后可以选择是否立即关闭对话框
            },
        )
    }

    if (showSaveSessionDialog) {
        SaveSessionDialog(
            initialTitle = currentSessionTitleInput.text, // 使用 currentSessionTitleInput
            onSave = { titleFromDialog ->
                handleSaveCurrentChatAndThen(titleFromDialog)
                // currentSessionTitleInput 更新已在 handleSaveCurrentChatAndThen 中
            },
            onDismiss = { showSaveSessionDialog = false }
        )
    }

    if (showSessionListDialog) {
        SessionListDialog(
            sessions = allSessionMetas.sortedByDescending { it.lastModifiedTimestamp },
            currentSessionId = currentSessionId,
            onSessionSelected = { sessionId -> tryLoadingSession(sessionId) },
            onDeleteSession = { sessionIdToDelete -> handleDeleteSession(sessionIdToDelete) },
            onRenameSession = { sessionIdToRename, newTitle ->
                handleRenameSession(
                    sessionIdToRename,
                    newTitle
                )
            },
            onDismiss = { showSessionListDialog = false }
        )
    }

    confirmDialogState?.let { state ->
        ConfirmActionDialog(
            title = state.title,
            text = state.text,
            onConfirm = state.onConfirm,
            onDismiss = { confirmDialogState = null }
        )
    }

    if (showActionMenuDialog && messageForAction != null) {
        MessageActionDialog(
            message = messageForAction!!,
            onDismiss = { showActionMenuDialog = false; messageForAction = null },
            onCopy = { msgToCopy ->
                val textToCopy = if (msgToCopy.thinkContent?.isNotBlank() == true) {
                    "${msgToCopy.content}\n<思考>\n${msgToCopy.thinkContent}\n</思考>"
                } else {
                    msgToCopy.content
                }
                clipboard.nativeClipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        msgToCopy.role,
                        textToCopy
                    )
                )
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                showActionMenuDialog = false; messageForAction = null
            },
            onDelete = { msgToDelete ->
                conversationHistory.remove(msgToDelete)
                markAsModified()
                if (msgToDelete.role == "system") { // 如果删除的是系统消息，重置激活的系统提示
                    activeSystemPrompt = globalDefaultSystemPrompt // 或 ""
                    updateSystemMessageInHistory(conversationHistory, activeSystemPrompt)
                }
                showActionMenuDialog = false; messageForAction = null
            },
            onRegenerate = { msgToRegenerate ->
                val currentMsgIndex =
                    conversationHistory.indexOfFirst { it.id == msgToRegenerate.id }
                if (currentMsgIndex != -1) {
                    when (msgToRegenerate.role) {
                        "user", "system" -> {
                            val history = conversationHistory.take(currentMsgIndex + 1).toList()
                            triggerLLMRequest(history, insertionIndex = currentMsgIndex + 1)
                        }

                        "assistant" -> {
                            if (currentMsgIndex > 0 || (currentMsgIndex == 0 && conversationHistory.any { it.role == "system" })) { // 允许基于纯系统消息重新生成
                                val history = conversationHistory.take(currentMsgIndex).toList()
                                conversationHistory.removeAt(currentMsgIndex)
                                markAsModified()
                                triggerLLMRequest(history, insertionIndex = currentMsgIndex)
                            } else Toast.makeText(
                                context,
                                "无法重新生成 (无足够上下文)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        else -> Toast.makeText(
                            context,
                            "不支持重新生成此类型消息",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                showActionMenuDialog = false; messageForAction = null
            },
            onEdit = { msgToEdit -> // 处理编辑请求
                messageToEditState = msgToEdit
                showEditMessageDialog = true
                showActionMenuDialog = false // 关闭操作菜单
                // messageForAction 设为 null 已在 onDismiss 中处理 (如果 MessageActionDialog 自己的 onDismiss 调用)
                // 但在这里也设置一下确保安全
                messageForAction = null
            }
        )
    }

    // 编辑消息对话框
    if (showEditMessageDialog && messageToEditState != null) {
        EditMessageDialog(
            initialContent = messageToEditState!!.content,
            onSave = { newContent ->
                val originalMessage = messageToEditState!!
                val editedMessage = originalMessage.copy(content = newContent) // 创建副本并修改内容

                val index = conversationHistory.indexOfFirst { it.id == originalMessage.id }
                if (index != -1) {
                    conversationHistory[index] = editedMessage
                    markAsModified()
                    Toast.makeText(context, "消息已更新", Toast.LENGTH_SHORT).show()
                }
                showEditMessageDialog = false
                messageToEditState = null
            },
            onDismiss = {
                showEditMessageDialog = false
                messageToEditState = null
            }
        )
    }
    if (showStreamingDialog && isLoading) {
        StreamingResponseDialog(
            content = streamingContent.value,
            onDismissRequest = { showStreamingDialog = false }, // 用户点击外部或“隐藏”按钮
            onInterruptStream = { interruptCurrentStream() }    // 用户点击“中断”按钮
        )
    }
}

fun updateSystemMessageInHistory(history: MutableList<MessageData>, systemPrompt: String) {
    val systemMessageIndex = history.indexOfFirst { it.role == "system" }
    if (systemPrompt.isNotBlank()) {
        val newSystemMessageData = MessageData(role = "system", content = systemPrompt)
        if (systemMessageIndex != -1) {
            if (history[systemMessageIndex].content != systemPrompt) { // 仅当内容不同时更新
                history[systemMessageIndex] = newSystemMessageData
            }
        } else {
            history.add(0, newSystemMessageData)
        }
    } else {
        if (systemMessageIndex != -1) {
            history.removeAt(systemMessageIndex)
        }
    }
}

suspend fun callLLMApi(
    apiUrl: String,
    apiKey: String,
    modelApiName: String,
    currentHistory: List<MessageData>, // 接收你的内部 MessageData 列表
    settings: ModelSettings,         // 接收包含所有模型参数的 ModelSettings 对象
    onChunkReceived: (String) -> Unit // 用于流式输出的回调
): String? = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        throw IOException("API 密钥为空。请在设置中配置API密钥。")
    }

    // 1. 将内部的 MessageData 列表转换为 API 请求所需的 ApiRequestMessage 列表
    val apiMessages = currentHistory
        .filterNot { it.role == "system" && it.content.isBlank() } // 不发送空白的系统消息
        .filter { it.role == "system" || it.role == "user" || it.role == "assistant" } // 确保角色有效
        .map { msg -> ApiRequestMessage(role = msg.role, content = msg.content) }

    // 2. 检查转换后的消息列表是否有效
    //    API 通常要求 'messages' 列表不能为空，或者至少包含一个用户消息。
    //    如果只有系统消息，某些 API 可能允许，某些可能不允许。
    //    你需要根据你使用的 API 的具体要求来调整这里的逻辑。
    //    例如，如果API要求至少有一个 'user' 角色的消息（除非是对话的开始，只有system prompt）：
    if (apiMessages.isEmpty() || (apiMessages.none { it.role == "user" } && apiMessages.any { it.role == "system" } && apiMessages.size == apiMessages.count { it.role == "system" })) {
        // 如果 apiMessages 为空，或者所有消息都是系统消息且没有用户消息。
        // （此条件可能需要根据API的具体要求调整，例如是否允许仅发送一个非空的系统消息）
        // 一个更简单的检查可能是：如果 apiMessages 为空，或者所有消息的角色都不是 "user"。
        if (apiMessages.none { it.role == "user" } && apiMessages.isNotEmpty()) {
            // 如果没有用户消息，但有系统/助手消息，某些API可能仍然不允许。
            // 这里的逻辑是：如果没有用户消息，且列表不为空（意味着只有系统/助手消息），我们也不发送。
            // 如果你的API允许以一个非空系统消息开始对话，则需要修改此条件。
            // **或者，一个更通用的检查：如果 `apiMessages` 为空，就直接报错或返回。**
            throw IOException("没有有效的用户消息可以发送到API。")
        }
        if (apiMessages.isEmpty()) { // 简化检查：如果过滤后没有可发送的消息
            throw IOException("没有有效的消息可以发送到API。")
        }
    }


    // 3. 构建 ApiRequestBody 对象
    //    对于可选参数，如果它们的值应该让API使用其默认值，则在ApiRequestBody中将它们设为null。
    //    `jsonRequestBuilder` (配置了 encodeDefaults = false 或默认不序列化 null) 会处理不发送这些字段。
    val requestBody = ApiRequestBody(
        model = modelApiName,
        messages = apiMessages,
        stream = true, // 通常总是 true 以支持流式输出
        temperature = if (settings.temperature != ModelSettings.DEFAULT.temperature) settings.temperature else null,
        frequencyPenalty = if (settings.frequencyPenalty != ModelSettings.DEFAULT.frequencyPenalty) settings.frequencyPenalty else null,
        topP = if (settings.topP < 1.0f && settings.topP != ModelSettings.DEFAULT.topP) settings.topP else null // 仅当 topP < 1.0 且非默认值时发送
        // maxTokens = settings.maxTokens // 如果添加了 maxTokens
    )
    // 条件 `settings.parameter != ModelSettings.DEFAULT.parameter` 是一种策略，
    // 意在只有当用户显式设置了与我们定义的“程序默认值”不同的值时才发送该参数。
    // 如果你想总是发送用户在UI上设置的值（即使它等于程序默认值），可以直接使用 `settings.parameter`。
    // 将其设为 null 的目的是让 API 使用它自己的默认值（如果我们的程序默认值和API默认值一致的话）。
    // 或者，如果某个参数设为 null 在 API 那里有特殊含义（比如“不启用”），这也是一种方法。

    // 4. 将请求对象序列化为 JSON 字符串
    val payloadString: String
    try {
        payloadString = jsonRequestBuilder.encodeToString(requestBody)
        // println("API Request Payload: $payloadString") // 打开此行用于调试，查看发送的JSON
    } catch (e: Exception) {
        // println("序列化API请求体时出错: ${e.message}")
        e.printStackTrace()
        throw IOException("无法构建API请求: ${e.message}", e)
    }

    // 5. 构建 OkHttp Request
    val request = Request.Builder()
        .url(apiUrl) // 确保 API_URL 是正确的常量
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .post(payloadString.toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    // 6. 执行请求并处理响应 (与之前相同)
    val fullResponseBuilder = StringBuilder()
    try {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无详细错误信息"
                throw IOException("API请求失败 (${response.code}): $errorBody")
            }
            val source = response.body?.source() ?: throw IOException("响应体为空")

            while (!source.exhausted()) {
                val line = source.readUtf8Line()
                if (line != null) {
                    if (line.startsWith("data: ")) {
                        val jsonData = line.substringAfter("data: ").trim()
                        if (jsonData.equals("[DONE]", ignoreCase = true)) {
                            break
                        }
                        if (jsonData.isNotBlank()) {
                            // extractContent 函数仍然用于解析流中的每一块 JSON 数据
                            extractContent(jsonData)?.let { contentChunk ->
                                onChunkReceived(contentChunk)
                                fullResponseBuilder.append(contentChunk)
                            }
                        }
                    } else if (line.trim().startsWith("{")) { // 处理非data:开头的JSON行
                        extractContent(line)?.let { contentChunk ->
                            onChunkReceived(contentChunk)
                            fullResponseBuilder.append(contentChunk)
                        }
                    }
                }
            }
            return@withContext if (fullResponseBuilder.isNotEmpty()) fullResponseBuilder.toString() else null
        }
    } catch (e: IOException) {
        println("网络请求或IO错误: ${e.message}")
        throw e
    } catch (e: Exception) {
        println("处理API响应时发生未知错误: ${e.message}")
        e.printStackTrace()
        throw IOException("处理API响应时发生错误: ${e.message}", e)
    }
}
