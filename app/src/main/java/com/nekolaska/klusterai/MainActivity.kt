package com.nekolaska.klusterai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- 配置信息 ---
const val DEFAULT_API_KEY_FALLBACK =
    "YOUR_DEFAULT_API_KEY_HERE" // SharedPreferences中无密钥时的备用值
const val API_URL = "https://api.kluster.ai/v1/chat/completions"
const val DEFAULT_SYSTEM_PROMPT = """你是甘雨。我是冰尘。
 【输出规则】：你仅扮演并输出关于甘雨的内容，以第三人称视角，我扮演冰尘，用互动的方式来推进剧情，期间你不可以控制冰尘的行动或是反应，不可以替我做出影响剧情的动作和语言。
 把甘雨的语言放在“”里，甘雨的外貌穿着表情动作身体等细节描写等等放在｛｝里，详细描写甘雨的外貌衣着发型。用第三人称视角描写，每个段落之间要求空一行。"""

// --- Model Data Definition ---
data class ModelInfo(val displayName: String, val apiName: String)

val availableModels = listOf(
    ModelInfo("DeepSeek-R1", "deepseek-ai/DeepSeek-R1"),
    ModelInfo("DeepSeek-V3-0324", "deepseek-ai/DeepSeek-V3-0324"),
    ModelInfo("Gemma 3 27B", "google/gemma-3-27b-it"),
    ModelInfo("Meta Llama 3.1 8B", "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo"),
    ModelInfo("Meta Llama 3.3 70B", "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo"),
    ModelInfo("Meta Llama 4 Maverick", "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8"),
    ModelInfo("Meta Llama 4 Scout", "meta-llama/Llama-4-Scout-17B-16E-Instruct"),
    ModelInfo("Mistral NeMo", "mistralai/Mistral-Nemo-Instruct-2407"),
    ModelInfo("Qwen2.5-VL 7B", "Qwen/Qwen2.5-VL-7B-Instruct"),
    ModelInfo("Qwen3-235B-A22B", "Qwen/Qwen3-235B-A22B-FP8")
)
val DEFAULT_MODEL_API_NAME =
    availableModels.firstOrNull()?.apiName ?: "deepseek-ai/DeepSeek-V3-0324"


// --- 消息数据类 ---
data class MessageData(
    val role: String,
    val content: String,
    val thinkContent: String? = null,
    val id: Long = System.nanoTime() // 使用纳秒保证ID的独特性
)

// --- OkHttp 客户端 ---
val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(300, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(300, TimeUnit.SECONDS)
    .build()

// --- 辅助函数 ---
fun extractContent(input: String): String? {
    return try {
        val lines = input.lines().filter { it.isNotBlank() }
        for (line in lines) {
            try {
                val jsonObject = JSONObject(line)
                if (jsonObject.has("choices")) {
                    val choicesArray = jsonObject.getJSONArray("choices")
                    if (choicesArray.length() > 0) {
                        val firstChoice = choicesArray.getJSONObject(0)
                        if (firstChoice.has("message")) {
                            return firstChoice.getJSONObject("message").getString("content")
                        } else if (firstChoice.has("delta") && firstChoice.getJSONObject("delta")
                                .has("content")
                        ) {
                            return firstChoice.getJSONObject("delta").getString("content")
                        }
                    }
                }
            } catch (_: Exception) { /* 忽略单行解析错误 */
            }
        }
        null
    } catch (e: Exception) {
        println("提取内容时出错: ${e.message}")
        null
    }
}

fun extractThinkSection(input: String): Pair<String?, String> {
    val regex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(input)
    val thinkPart = match?.groupValues?.get(1)?.trim()
    val remainingPart = regex.replace(input, "").trim()
    return Pair(thinkPart, remainingPart)
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
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
    //var newTitleInput by remember { mutableStateOf("") }

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
                                    //newTitleInput = session.title
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

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault() // 使用设备本地时区
    return sdf.format(Date(timestamp))
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

//@Composable
//fun ResetConfirmationDialog(
//    onConfirm: () -> Unit,
//    onDismiss: () -> Unit
//) {
//    AlertDialog(
//        onDismissRequest = onDismiss,
//        title = { Text("确认重置") },
//        text = { Text("您确定要重置当前聊天吗？所有聊天记录将被清除。") },
//        confirmButton = {
//            TextButton(
//                onClick = {
//                    onConfirm()
//                    onDismiss()
//                }
//            ) {
//                Text("确认")
//            }
//        },
//        dismissButton = {
//            TextButton(onClick = onDismiss) {
//                Text("取消")
//            }
//        },
//        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
//        shape = MaterialTheme.shapes.large
//    )
//}

// --- SettingsDialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isGlobalSettingsMode: Boolean, // true 表示修改全局默认，false 表示修改当前会话特定设置
    currentApiKey: String, // 仅在全局模式下可编辑
    currentSelectedModelApiName: String,
    currentSystemPrompt: String,
    onSaveGlobalDefaults: (apiKey: String, modelApiName: String, systemPrompt: String) -> Unit,
    onSaveSessionSpecific: (modelApiName: String, systemPrompt: String) -> Unit,
    onDismiss: () -> Unit
) {
    // 如果是会话特定设置，API Key 不应在此处修改，因此使用传入的 currentApiKey (通常是全局的)
    var apiKeyInput by remember(currentApiKey, isGlobalSettingsMode) {
        mutableStateOf(if (isGlobalSettingsMode) currentApiKey else "") // API Key 输入框仅全局模式可见和可编辑
    }
    var selectedModelApiNameState by remember(currentSelectedModelApiName) {
        mutableStateOf(
            currentSelectedModelApiName
        )
    }
    var systemPromptInput by remember(currentSystemPrompt) { mutableStateOf(currentSystemPrompt) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGlobalSettingsMode) "全局默认设置" else "当前会话设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isGlobalSettingsMode) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { if (it.length <= 100) apiKeyInput = it },
                        label = { Text("API 密钥 (全局)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        singleLine = true,
                        placeholder = { Text("在此输入您的 API Key") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text("选择模型:", style = MaterialTheme.typography.titleSmall)
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = availableModels.find { it.apiName == selectedModelApiNameState }?.displayName
                            ?: "选择模型",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("当前模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable) //  设置只读
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        model.displayName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                onClick = {
                                    selectedModelApiNameState = model.apiName
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = systemPromptInput,
                    onValueChange = { if (it.length <= 3000) systemPromptInput = it },
                    label = { Text("系统提示内容") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 250.dp)
                        .padding(vertical = 8.dp),
                    minLines = 3,
                    maxLines = 10,
                    placeholder = { Text(if (isGlobalSettingsMode) "输入全局默认系统提示..." else "输入当前会话的系统提示...") }
                )
                if (!isGlobalSettingsMode) {
                    Text(
                        "注意：此处的更改将应用于当前打开的会话，并在您手动保存会话后持久化。",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isGlobalSettingsMode) {
                    onSaveGlobalDefaults(apiKeyInput, selectedModelApiNameState, systemPromptInput)
                } else {
                    onSaveSessionSpecific(selectedModelApiNameState, systemPromptInput)
                }
                onDismiss() // 关闭对话框
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}


// --- ChatScreen ---
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 全局状态 ---
    var globalApiKey by remember {
        mutableStateOf(SharedPreferencesUtils.loadApiKey(context, DEFAULT_API_KEY_FALLBACK))
    }
    var globalDefaultModelApiName by remember {
        mutableStateOf(SharedPreferencesUtils.loadSelectedModel(context, DEFAULT_MODEL_API_NAME))
    }
    var globalDefaultSystemPrompt by remember {
        mutableStateOf(SharedPreferencesUtils.loadSystemPrompt(context, DEFAULT_SYSTEM_PROMPT))
    }

    // --- 当前会话相关状态 ---
    var currentSessionId by remember { mutableStateOf<String?>(null) } // 当前打开的会话ID，null表示新聊天（未保存）
    var currentSessionTitleInput by remember { mutableStateOf(TextFieldValue("新聊天")) } // 用于保存对话框的标题输入
    val conversationHistory = remember { mutableStateListOf<MessageData>() }
    var isCurrentChatModified by remember { mutableStateOf(false) } // 当前聊天自上次保存以来是否有修改

    // 当前激活的会话设置 (从SessionMeta加载，或使用全局默认)
    var activeModelApiName by remember { mutableStateOf(globalDefaultModelApiName) }
    var activeSystemPrompt by remember { mutableStateOf(globalDefaultSystemPrompt) }


    // --- UI 控制状态 ---
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    // 这个 Deprecated 不会改
    val clipboardManager = LocalClipboardManager.current

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showActionMenuDialog by remember { mutableStateOf(false) }
    var messageForAction by remember { mutableStateOf<MessageData?>(null) }
    var showStreamingDialog by remember { mutableStateOf(false) }
    val streamingContent = remember { mutableStateOf("") }
    var showSaveSessionDialog by remember { mutableStateOf(false) }
    var showSessionListDialog by remember { mutableStateOf(false) }

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
        } else { // 新聊天或无会话数据时，使用全局默认
            activeModelApiName = globalDefaultModelApiName
            activeSystemPrompt = globalDefaultSystemPrompt
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
        if (isCurrentChatModified) {
            confirmDialogState = ConfirmDialogState(
                title = "未保存的更改",
                text = "当前聊天有未保存的更改。开始新聊天将丢失这些更改。确定要继续吗？",
                onConfirm = { startNewChatConfirmed() }
            )
        } else {
            startNewChatConfirmed()
        }
    }

    fun tryLoadingSession(sessionId: String) {
        if (currentSessionId == sessionId) return // 已经是当前会话

        val action = {
            loadSession(sessionId)
            showSessionListDialog = false // 关闭列表
        }
        if (isCurrentChatModified) {
            confirmDialogState = ConfirmDialogState(
                title = "未保存的更改",
                text = "切换会话前是否保存当前聊天？若不保存，更改将丢失。",
                onConfirm = {
                    // 这里可以先触发保存当前会话的逻辑，或者直接切换
                    // 为简单起见，我们先直接切换，丢失更改
                    action()
                }
            )
        } else {
            action()
        }
    }


    fun handleSaveSession(title: String) {
        val sessionIdToSave = currentSessionId ?: ChatSessionRepository.generateNewSessionId()
        val newMeta = SessionMeta(
            id = sessionIdToSave,
            title = title,
            lastModifiedTimestamp = System.currentTimeMillis(),
            systemPrompt = activeSystemPrompt, // 保存当前激活的设置
            modelApiName = activeModelApiName
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
        Toast.makeText(context, "会话已保存: $title", Toast.LENGTH_SHORT).show()
        showSaveSessionDialog = false
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
        showStreamingDialog = true

        coroutineScope.launch {
            var fullResponse: String?
            try {
                fullResponse = callLLMApi(
                    apiKey = globalApiKey,
                    modelApiName = activeModelApiName, // 使用当前会话的激活模型
                    currentHistory = actualHistory,
                    onChunkReceived = { chunk -> streamingContent.value += chunk }
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
    BackHandler(enabled = isCurrentChatModified) {
        confirmDialogState = ConfirmDialogState(
            title = "未保存的更改",
            text = "当前聊天有未保存的更改。确定要退出应用吗？",
            onConfirm = { (context as? ComponentActivity)?.finish() } // 退出应用
        )
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
                        Icon(Icons.Filled.Create, contentDescription = "保存会话")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "打开设置")
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
                            userInput = ""
                            triggerLLMRequest(historyForApiCall)
                        }
                    },
                    isLoading = isLoading,
                    apiKey = globalApiKey
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { tryStartingNewChat() }) {
                Icon(Icons.Filled.Add, "新建聊天")
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
                    MessageBubble(message = message, onLongClick = { msg ->
                        messageForAction = msg
                        showActionMenuDialog = true
                    })
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
            onSaveGlobalDefaults = { newApiKey, newModel, newPrompt ->
                globalApiKey = newApiKey
                globalDefaultModelApiName = newModel
                globalDefaultSystemPrompt = newPrompt
                SharedPreferencesUtils.saveApiKey(context, newApiKey)
                SharedPreferencesUtils.saveSelectedModel(context, newModel)
                SharedPreferencesUtils.saveSystemPrompt(context, newPrompt)

                if (currentSessionId == null) { // 如果当前是新聊天，立即应用全局更改
                    updateActiveSessionSettings(null)
                }
                Toast.makeText(context, "全局默认设置已保存", Toast.LENGTH_SHORT).show()
                showSettingsDialog = false
            },
            onSaveSessionSpecific = { newModel, newPrompt -> // 仅当 currentSessionId != null 时
                activeModelApiName = newModel
                activeSystemPrompt = newPrompt
                updateSystemMessageInHistory(conversationHistory, newPrompt) // 更新内存中的系统消息
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
                handleSaveSession(titleFromDialog)
                // currentSessionTitleInput 更新已在 handleSaveSession 中
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
                clipboardManager.setText(AnnotatedString(textToCopy))
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

@Composable
fun InputRow(
    userInput: String,
    onUserInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    apiKey: String
) {
    Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = userInput,
                onValueChange = { if (it.length <= 2000) onUserInputChange(it) },
                placeholder = { Text("输入您的消息...") },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                singleLine = true, // 或者根据需要设置为多行
                // maxLines = 5, // 如果允许多行
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                enabled = !isLoading && userInput.isNotBlank() && apiKey.isNotBlank()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: MessageData, onLongClick: (MessageData) -> Unit) {
    val alignment = if (message.role == "user") Alignment.End else Alignment.Start
    val containerColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.primaryContainer
        "assistant" -> MaterialTheme.colorScheme.secondaryContainer
        "system" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (message.role) {
        "user" -> MaterialTheme.colorScheme.onPrimaryContainer
        "assistant" -> MaterialTheme.colorScheme.onSecondaryContainer
        "system" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var isThinkExpanded by remember { mutableStateOf(false) } // 控制思考内容展开/折叠

    if (message.role == "system" && message.content.isBlank()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current

        // containerSize 返回的是像素值，需要转换为 Dp
        val containerWidthPx = windowInfo.containerSize.width
        val containerWidthDp = remember(containerWidthPx, density) {
            with(density) { containerWidthPx.toDp() }
        }

        // 使用 containerWidthDp 进行布局计算
        val maxWidth = remember(containerWidthDp) { containerWidthDp * 0.8f }

        //        val configuration = LocalConfiguration.current
        //        val maxWidth =
        //            remember(configuration.screenWidthDp) { configuration.screenWidthDp.dp * 0.85f } // 稍微增加宽度

        Surface(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .clip(MaterialTheme.shapes.medium)
                .combinedClickable(
                    onClick = { /* 短按无操作，或未来可用于编辑等 */ },
                    onLongClick = { onLongClick(message) }
                ),
            color = containerColor,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text( // 角色名称
                    text = when (message.role) {
                        "user" -> "你"
                        "assistant" -> "助手"
                        "system" -> "系统提示"
                        else -> message.role.replaceFirstChar { it.uppercase() }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp)) // 角色和内容之间的间距

                // 主要内容
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )

                // 思考内容部分
                if (message.role != "system" && message.thinkContent != null && message.thinkContent.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton( // 展开/折叠思考内容的按钮
                        onClick = { isThinkExpanded = !isThinkExpanded },
                        modifier = Modifier.padding(vertical = 0.dp), // 减小按钮的垂直内边距
                        contentPadding = PaddingValues(
                            horizontal = 4.dp,
                            vertical = 2.dp
                        ) // 调整按钮内容内边距
                    ) {
                        Text(
                            if (isThinkExpanded) "隐藏思考" else "显示思考",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.7f)
                        )
                        Icon(
                            imageVector = if (isThinkExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isThinkExpanded) "隐藏思考" else "显示思考",
                            modifier = Modifier.size(16.dp),
                            tint = contentColor.copy(alpha = 0.7f)
                        )
                    }

                    AnimatedVisibility(visible = isThinkExpanded) { // 可动画的可见性
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp) // 与按钮的间距
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), // 更淡的背景
                                    RoundedCornerShape(4.dp) // 圆角
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp) // 内边距
                        ) {
                            SelectionContainer { // 允许复制思考内容
                                Text(
                                    text = message.thinkContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = contentColor.copy(alpha = 0.85f) // 思考内容颜色可以略深一点
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageActionDialog(
    message: MessageData,
    onDismiss: () -> Unit,
    onCopy: (MessageData) -> Unit,
    onDelete: (MessageData) -> Unit,
    onRegenerate: (MessageData) -> Unit // 新增重新生成回调
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "操作消息",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "角色: ${message.role.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "内容: \"${message.content.take(80)}${if (message.content.length > 80) "..." else ""}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                message.thinkContent?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "思考: \"${it.take(50)}${if (it.length > 50) "..." else ""}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                // 复制按钮
                TextButton(
                    onClick = { onCopy(message) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "复制",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("复制消息")
                }
                Spacer(Modifier.height(4.dp))

                // 重新回复按钮 (仅对 user 和 assistant 消息有意义，但为简化，都显示)
                // 你可以根据 message.role 决定是否显示此按钮
                // if (message.role == "user" || message.role == "assistant")
                TextButton(
                    onClick = { onRegenerate(message) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "重新回复",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("重新回复")
                }
                Spacer(Modifier.height(4.dp))


                // 删除按钮
                TextButton(
                    onClick = { onDelete(message) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("删除消息")
                }
            }
        },
        confirmButton = { // 只保留一个取消按钮，因为操作按钮都在列表里了
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text("取消")
                }
            }
        },
        dismissButton = null, // 移除单独的 DismissButton
        icon = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
fun StreamingResponseDialog(
    content: String,
    onDismissRequest: () -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(content) { // 当内容变化时，滚动到底部
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("实时回复中...") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp) // 限制最大高度
                    .verticalScroll(scrollState) // 使其可滚动
            ) {
                SelectionContainer { // 允许选择和复制文本
                    Text(
                        text = content.ifBlank { "等待响应..." }, // 内容为空时的提示
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "隐藏",
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text("隐藏")
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, // 使用较低的容器颜色
        shape = MaterialTheme.shapes.large
    )
}

suspend fun callLLMApi(
    apiKey: String,
    modelApiName: String,
    currentHistory: List<MessageData>,
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

    if (messagesJsonArray.length() == 0) {
        // 如果过滤后消息列表为空 (例如，只有一条空白的系统消息)，则不发送请求
        // throw IOException("没有有效的消息可以发送到API。") // 或者返回 null/特定错误
        return@withContext null // 避免API调用
    }


    val payload = JSONObject().apply {
        put("model", modelApiName)
        put("messages", messagesJsonArray)
        put("stream", true) // 启用流式输出
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


// --- 预览 ---
//@Preview(showBackground = true)
//@Composable
//fun DefaultPreview() {
//    MaterialTheme {
//        ChatScreen()
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//fun MessageBubbleUserPreview() {
//    MaterialTheme {
//        MessageBubble(MessageData("user", "你好，甘雨！")) {}
//    }
//}
//
//@Preview(showBackground = true)
//@Composable
//fun MessageBubbleAssistantWithThinkPreview() {
//    var expanded by remember { mutableStateOf(false) }
//    MaterialTheme {
//        Column {
//            MessageBubble(
//                MessageData(
//                    "assistant",
//                    "{甘雨眨了眨漂亮的紫色眼睛} “你好，冰尘。”",
//                    "用户打招呼了。我应该也问候他们。这是一个比较长的思考过程，用于测试多行显示和滚动效果。思考内容可以帮助理解AI的决策过程。"
//                )
//            ) {}
//            Spacer(modifier = Modifier.height(10.dp))
//            MessageBubble( // 另一个没有思考内容的消息
//                MessageData(
//                    "user",
//                    "今天天气怎么样？"
//                )
//            ) {}
//        }
//    }
//}
//
//@Preview
//@Composable
//fun SettingsDialogPreview() {
//    MaterialTheme {
//        SettingsDialog(
//            currentApiKey = "test-key-123",
//            currentSelectedModelApiName = availableModels.first().apiName,
//            currentSystemPrompt = "你是一个乐于助人的AI助手。",
//            onSave = { _, _, _ -> },
//            onDismiss = {}
//        )
//    }
//}
//
//
//@Preview
//@Composable
//fun MessageActionDialogPreview() {
//    MaterialTheme {
//        MessageActionDialog(
//            message = MessageData(
//                "assistant",
//                "这是一条可以操作的消息内容，可能有点长，看看会不会溢出或者正确显示省略号。",
//                "这是思考内容，也可能很长。"
//            ),
//            onDismiss = {},
//            onCopy = {},
//            onDelete = {},
//            onRegenerate = {}
//        )
//    }
//}
//
//@Preview
//@Composable
//fun StreamingResponseDialogPreview() {
//    MaterialTheme {
//        StreamingResponseDialog(
//            content = "这是第一段回复。\n这是第二段回复，内容还在不断增加中...\n又来了一段新的内容，看看滚动条是否正常工作。",
//            onDismissRequest = {}
//        )
//    }
//}