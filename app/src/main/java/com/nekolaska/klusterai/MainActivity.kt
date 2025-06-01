package com.nekolaska.klusterai

import android.content.ClipData
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nekolaska.klusterai.ui.components.EditMessageDialog
import com.nekolaska.klusterai.ui.components.InputRow
import com.nekolaska.klusterai.ui.components.MessageActionDialog
import com.nekolaska.klusterai.ui.components.MessageBubble
import com.nekolaska.klusterai.ui.components.SettingsDialog
import com.nekolaska.klusterai.ui.components.StreamingResponseDialog
import com.nekolaska.klusterai.ui.theme.KlusterAITheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

// --- 消息数据类 ---
data class MessageData(
    val role: String,
    val content: String,
    val thinkContent: String? = null,
    val id: Long = System.nanoTime() // 使用纳秒保证ID的独特性
)

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

// Session 相关
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveSessionDialog(
    initialTitle: String,
    onSave: (title: String) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember {
        mutableStateOf(
            TextFieldValue(
                initialTitle,
                TextRange(initialTitle.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存会话") },
        text = {
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("会话标题") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (titleInput.text.isNotBlank()) {
                        onSave(titleInput.text)
                    }
                    keyboardController?.hide()
                })
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (titleInput.text.isNotBlank()) {
                        onSave(titleInput.text)
                    }
                },
                enabled = titleInput.text.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListDialog(
    sessions: List<SessionMeta>,
    currentSessionId: String?,
    onSessionSelected: (sessionId: String) -> Unit,
    onDeleteSession: (sessionId: String) -> Unit,
    onRenameSession: (sessionId: String, newTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sessionToRename by remember { mutableStateOf<SessionMeta?>(null) }

    Dialog(onDismissRequest = {
        sessionToRename = null // 关闭重命名输入时重置
        onDismiss()
    }) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "会话列表",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                if (sessions.isEmpty()) {
                    Text(
                        "还没有保存的会话。",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) { // 限制高度，使其可滚动
                        items(sessions, key = { it.id }) { session ->
                            SessionListItem(
                                session = session,
                                isCurrent = session.id == currentSessionId,
                                onClick = { onSessionSelected(session.id) },
                                onDelete = { onDeleteSession(session.id) },
                                onRename = {
                                    sessionToRename = session
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("关闭")
                }
            }
        }
    }

    // 重命名对话框 (嵌套或作为另一个AlertDialog)
    if (sessionToRename != null) {
        var renameTitleField by remember(sessionToRename) {
            mutableStateOf(
                TextFieldValue(
                    sessionToRename!!.title,
                    selection = TextRange(sessionToRename!!.title.length)
                )
            )
        }
        val renameFocusRequester = remember { FocusRequester() }

        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameTitleField,
                    onValueChange = { renameTitleField = it },
                    label = { Text("新标题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(renameFocusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (renameTitleField.text.isNotBlank()) {
                            onRenameSession(sessionToRename!!.id, renameTitleField.text)
                            sessionToRename = null
                        }
                    })
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameTitleField.text.isNotBlank()) {
                            onRenameSession(sessionToRename!!.id, renameTitleField.text)
                            sessionToRename = null
                        }
                    },
                    enabled = renameTitleField.text.isNotBlank()
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { sessionToRename = null }) { Text("取消") } }
        )
        LaunchedEffect(sessionToRename) {
            if (sessionToRename != null) renameFocusRequester.requestFocus()
        }
    }
}

@Composable
fun SessionListItem(
    session: SessionMeta,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    //val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                "最后修改: ${formatTimestamp(session.lastModifiedTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("重命名") }, onClick = {
                    onRename()
                    showMenu = false
                })
                DropdownMenuItem(text = { Text("删除") }, onClick = {
                    onDelete() // onDelete 应该触发确认对话框
                    showMenu = false
                })
            }
        }
    }
}

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text("确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// --- ChatScreen ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current // 获取生命周期所有者
    val coroutineScope = rememberCoroutineScope()

    // --- 全局状态 ---
    var autoSaveOnSwitchSessionGlobalPref by remember { //全局自动保存偏好
        mutableStateOf(SharedPreferencesUtils.loadAutoSaveOnSwitchPreference(context))
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
    data class ConfirmDialogState(val title: String, val text: String, val onConfirm: () -> Unit)

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
        // ... (原 handleSaveSession 逻辑)
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
    LaunchedEffect(Unit) {
        allSessionMetas.addAll(ChatSessionRepository.loadSessionMetas(context))
        if (allSessionMetas.isNotEmpty()) {
            loadSession(allSessionMetas.first().id) // 默认加载最新的
        } else {
            prepareNewChat() // 没有会话则开始新聊天
        }
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
    // --- API 请求 ---
    fun triggerLLMRequest(historyForRequest: List<MessageData>, insertionIndex: Int? = null) {
        if (globalApiKey.isBlank()) {
            errorMessage = "API 密钥为空，请在全局设置中填写。"
            return
        }
        val actualHistory = historyForRequest.toMutableList()
        // 确保 System Prompt 在请求历史中是正确的
        val systemMsgIndex = actualHistory.indexOfFirst { it.role == "system" }
        if (activeSystemPrompt.isNotBlank()) {
            val sysMsg = MessageData("system", activeSystemPrompt)
            if (systemMsgIndex != -1) actualHistory[systemMsgIndex] = sysMsg
            else actualHistory.add(0, sysMsg)
        } else {
            if (systemMsgIndex != -1) actualHistory.removeAt(systemMsgIndex)
        }

        if (actualHistory.filterNot { it.role == "system" && it.content.isBlank() }.isEmpty()) {
            errorMessage = "无法在没有有效用户消息的情况下发送请求。"
            isLoading = false
            return
        }

        errorMessage = null
        isLoading = true
        streamingContent.value = ""
        // 根据设置决定是否立即显示流式对话框
        showStreamingDialog = activeModelSettings.autoShowStreamingDialog

        coroutineScope.launch {
            var fullResponse: String?
            try {
                fullResponse = callLLMApi(
                    apiKey = globalApiKey,
                    modelApiName = activeModelApiName, // 使用当前会话的激活模型
                    currentHistory = actualHistory,
                    onChunkReceived = { chunk -> streamingContent.value += chunk },
                    settings = activeModelSettings, // 传递 activeModelSettings
                )
                val newAssistantMessage = if (fullResponse != null && fullResponse.isNotBlank()) {
                    val (thinkPart, remainingPart) = extractThinkSection(fullResponse)
                    MessageData(
                        role = "assistant",
                        content = remainingPart,
                        thinkContent = thinkPart
                    )
                } else if (streamingContent.value.isNotBlank() && fullResponse.isNullOrBlank()) {
                    val (thinkPart, remainingPart) = extractThinkSection(streamingContent.value)
                    MessageData(
                        role = "assistant",
                        content = remainingPart,
                        thinkContent = thinkPart
                    )
                } else {
                    errorMessage = "从API收到空响应或无效响应。"
                    null
                }
                newAssistantMessage?.let { msg ->
                    if (insertionIndex != null && insertionIndex >= 0 && insertionIndex <= conversationHistory.size) {
                        conversationHistory.add(insertionIndex, msg)
                    } else {
                        conversationHistory.add(msg)
                    }
                    markAsModified()
                }
            } catch (e: IOException) {
                errorMessage = "API 调用错误: ${e.message}"; e.printStackTrace()
            } catch (e: Exception) {
                errorMessage = "发生未知错误: ${e.message}"; e.printStackTrace()
            } finally {
                isLoading = false
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("助手正在回复...", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            showStreamingDialog = true
                        }) { Text("显示实时回复") }
                    }
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
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
                reverseLayout = false // 正常顺序
            ) {
                items(conversationHistory, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        isContentSelectable = activeModelSettings.isTextSelectableInBubble,
                        onLongClick = { msg ->
                            messageForAction = msg
                            showActionMenuDialog = true
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            LaunchedEffect(conversationHistory.size) {
                if (conversationHistory.isNotEmpty()) {
                    listState.animateScrollToItem(conversationHistory.size - 1) // 滚动到最新消息
                }
            }
            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // --- Dialogs Rendering ---
    if (showSettingsDialog) {
        SettingsDialog(
            isGlobalSettingsMode = currentSessionId == null, // 如果当前没有打开的会话，则为全局设置模式
            currentApiKey = globalApiKey,
            currentSelectedModelApiName = activeModelApiName,
            currentSystemPrompt = activeSystemPrompt,
            currentAutoSaveOnSwitch = autoSaveOnSwitchSessionGlobalPref, // 传递当前的全局偏好
            currentModelSettings = activeModelSettings, // 传递 activeModelSettings
            onSaveGlobalDefaults = { newAutoSavePref, newApiKey, newModel, newPrompt, newSettings ->
                globalApiKey = newApiKey
                globalDefaultModelApiName = newModel
                globalDefaultSystemPrompt = newPrompt
                globalDefaultModelSettings = newSettings // 保存新的全局默认模型设置
                autoSaveOnSwitchSessionGlobalPref = newAutoSavePref // 更新全局偏好状态

                SharedPreferencesUtils.apply {
                    saveApiKey(context, newApiKey)
                    saveSelectedModel(context, newModel)
                    saveSystemPrompt(context, newPrompt)
                    saveGlobalModelSettings(context, newSettings)
                    saveAutoSaveOnSwitchPreference(context, newAutoSavePref) // 保存到 SP
                }

                if (currentSessionId == null) { // 如果当前是新聊天，立即应用全局更改
                    updateActiveSessionSettings(null)
                }
                Toast.makeText(context, "全局默认设置已保存", Toast.LENGTH_SHORT).show()
                showSettingsDialog = false
            },
            onSaveSessionSpecific = { newModel, newPrompt, newSettings ->
                activeModelApiName = newModel
                activeSystemPrompt = newPrompt
                activeModelSettings = newSettings // 更新当前会话激活的模型设置
                updateSystemMessageInHistory(conversationHistory, newPrompt)
                markAsModified()
                Toast.makeText(context, "当前会话设置已更新 (待保存)", Toast.LENGTH_SHORT).show()
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
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
            onDismissRequest = { showStreamingDialog = false }
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
    apiKey: String,
    modelApiName: String,
    currentHistory: List<MessageData>,
    settings: ModelSettings, // 传递 ModelSettings 对象
    onChunkReceived: (String) -> Unit // 用于流式输出的回调
): String? = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) {
        throw IOException("API 密钥为空。请在设置中配置API密钥。")
    }
    val messagesJsonArray = JSONArray()
    currentHistory
        .filterNot { it.role == "system" && it.content.isBlank() } // 不发送空白的系统消息
        .forEach { msg ->
            // API通常只接受 system, user, assistant 角色
            if (msg.role == "system" || msg.role == "user" || msg.role == "assistant") {
                messagesJsonArray.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }
        }

    if (messagesJsonArray.length() == 0 && currentHistory.none { it.role == "user" }) {
        // 确保至少有一个非系统消息或一个有效的系统消息
        // 如果只有空白的系统消息，或者完全没有消息，则不发送
        // 如果过滤后消息列表为空 (例如，只有一条空白的系统消息)，则不发送请求
        throw IOException("没有有效的消息可以发送到API。") // 避免API调用
    }


    val payload = JSONObject().apply {
        put("model", modelApiName)
        put("messages", messagesJsonArray)
        put("stream", true) // 启用流式输出
        put("temperature", settings.temperature)
        put("frequency_penalty", settings.frequencyPenalty)
        if (settings.topP < 1.0f) { // 默认即为 1.0
            put("top_p", settings.topP)
        }
        // if (settings.maxTokens != null) put("max_tokens", settings.maxTokens)
    }

    val request = Request.Builder()
        .url(API_URL)
        .header("Authorization", "Bearer $apiKey")
        .header("Content-Type", "application/json")
        .post(payload.toString().toRequestBody("application/json".toMediaTypeOrNull()))
        .build()

    val fullResponseBuilder = StringBuilder()
    try {
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "无详细错误信息"
                throw IOException("API请求失败 (${response.code}): $errorBody")
            }
            val source = response.body?.source() ?: throw IOException("响应体为空")

            // 处理流式数据
            while (!source.exhausted()) {
                val line = source.readUtf8Line() // 读取一行
                if (line != null) {
                    if (line.startsWith("data: ")) {
                        val jsonData = line.substringAfter("data: ").trim()
                        if (jsonData.equals("[DONE]", ignoreCase = true)) {
                            break // 流结束标记
                        }
                        if (jsonData.isNotBlank()) {
                            extractContent(jsonData)?.let { contentChunk ->
                                onChunkReceived(contentChunk) // 回调每个数据块
                                fullResponseBuilder.append(contentChunk)
                            }
                        }
                    } else if (line.trim().startsWith("{")) { // 有些API错误或非流式响应可能直接是JSON对象
                        extractContent(line)?.let { contentChunk ->
                            onChunkReceived(contentChunk)
                            fullResponseBuilder.append(contentChunk)
                        }
                    }
                }
            }
            // 确保即使没有流式数据，如果响应体本身是完整JSON且包含内容，也能提取
            //if (fullResponseBuilder.isEmpty() && response.body?.contentType()?.subtype == "json") {
            // 这种情况比较少见，因为我们请求了 stream=true
            // 但作为备用，可以尝试解析整个body
            //}
            return@withContext if (fullResponseBuilder.isNotEmpty()) fullResponseBuilder.toString() else null
        }
    } catch (e: IOException) {
        println("网络请求或IO错误: ${e.message}") // 打印到控制台便于调试
        throw e // 重新抛出，让调用者处理
    } catch (e: Exception) {
        println("处理API响应时发生未知错误: ${e.message}")
        e.printStackTrace()
        throw IOException("处理API响应时发生错误: ${e.message}", e) // 包装成IOException
    }
}
