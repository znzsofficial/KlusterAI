package com.nekolaska.klusterai.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import com.nekolaska.klusterai.data.ModelSettings
import com.nekolaska.klusterai.data.availableModels
import java.util.Locale
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager // 导入 Pager
import androidx.compose.foundation.pager.rememberPagerState // 导入 PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab // 导入 Tab
import androidx.compose.material3.TabRow // 导入 TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nekolaska.klusterai.BackupRestoreManager
import com.nekolaska.klusterai.DEFAULT_API_URL
import com.nekolaska.klusterai.ImportConflictStrategy
import com.nekolaska.klusterai.R
import com.nekolaska.klusterai.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date


@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun SettingsDialog(
    viewModel: SettingsViewModel = viewModel(), // 获取 ViewModel 实例

    // 回调
    onSaveGlobalDefaults: (
        autoSave: Boolean, autoVerify: Boolean,
        autoShowStreaming: Boolean, isTextSelectable: Boolean,
        apiKey: String, apiUrl: String, modelApiName: String, systemPrompt: String, modelSettings: ModelSettings
    ) -> Unit,
    onUpdateCurrentSessionSettings: ( // 当用户在“当前会话”页修改时，立即更新 ChatScreen 中的 activeXXX 状态
        modelApiName: String, systemPrompt: String, modelSettings: ModelSettings
    ) -> Unit,
    onDismiss: () -> Unit,
    hasActiveSession: Boolean, // 标记是否有活动会话，用于启用/禁用“当前会话”Tab
    onChatHistoryImportedParent: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState() // 订阅 UI 状态
    val pagerState =
        rememberPagerState(pageCount = { if (hasActiveSession) 2 else 1 }) // 如果没有活动会话，只有全局页
    val coroutineScope = rememberCoroutineScope()

    // 当 Pager 实际滚动完成时，更新 ViewModel 中的 currentPagerPage
    LaunchedEffect(pagerState.currentPage) {
        viewModel.setCurrentPagerPage(pagerState.currentPage)
    }
    // 当 ViewModel 中的 currentPagerPage (可能由外部逻辑改变) 变化时，滚动 Pager
    LaunchedEffect(uiState.currentPagerPage) {
        if (pagerState.currentPage != uiState.currentPagerPage) {
            coroutineScope.launch { pagerState.animateScrollToPage(uiState.currentPagerPage) }
        }
    }


    val pages = remember(hasActiveSession) {
        mutableListOf("全局默认").apply { if (hasActiveSession) add("当前会话") }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用设置") }, // 通用标题
        text = {
            Column(modifier = Modifier.wrapContentHeight()) {
                if (pages.size > 1) { // 只有当有多于一个页面时才显示 TabRow
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // 使Tab背景与对话框内容区域一致
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant, // Tab文本颜色
                        indicator = { tabPositions -> // 自定义指示器
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator( // 使用更细的指示器
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    height = 2.dp, // 指示器高度
                                    color = MaterialTheme.colorScheme.primary // 指示器颜色
                                )
                            }
                        },
                        divider = {} // 移除 TabRow 下方的默认分隔线
                    ) {
                        pages.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(
                                            index
                                        )
                                    }
                                },
                                text = {
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }, // 使用稍大一点的标签字体
                                selectedContentColor = MaterialTheme.colorScheme.primary, // 选中时的文本颜色
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant // 未选中时的文本颜色
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f, fill = false)// fill = false 允许 Pager 根据内容收缩高度
                        .wrapContentHeight()       // 再次尝试让 Pager 包裹内容
                        .verticalScroll(rememberScrollState()) // 让 Pager 内容可滚动
                ) { pageIndex ->
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .wrapContentHeight()
                    ) {
                        when (pageIndex) {
                            0 -> GlobalSettingsPage(
                                apiKey = uiState.apiKey,
                                onApiKeyChange = viewModel::updateApiKey,
                                apiUrl = uiState.apiUrl,
                                onApiUrlChange = viewModel::updateApiUrl,
                                modelApiName = uiState.globalModelApiName,
                                onModelApiNameChange = viewModel::updateGlobalModelApiName,
                                systemPrompt = uiState.globalSystemPrompt,
                                onSystemPromptChange = viewModel::updateGlobalSystemPrompt,
                                autoSave = uiState.globalAutoSaveOnSwitch,
                                onAutoSaveChange = viewModel::updateGlobalAutoSave,
                                autoVerify = uiState.globalAutoVerifyResponse,
                                onAutoVerifyChange = viewModel::updateGlobalAutoVerify,
                                // 从 uiState.globalModelSettings 中取值
                                temp = uiState.globalModelSettings.temperature,
                                onTempChange = viewModel::updateGlobalTemperature,
                                freqP = uiState.globalModelSettings.frequencyPenalty,
                                onFreqPChange = viewModel::updateGlobalFrequencyPenalty,
                                topP = uiState.globalModelSettings.topP,
                                onTopPChange = viewModel::updateGlobalTopP,
                                // 全局的 autoShow 和 textSelectable
                                autoShowDialog = uiState.globalAutoShowStreamingDialog,
                                onAutoShowDialogChange = viewModel::updateGlobalAutoShowDialog,
                                isTextSelectable = uiState.globalIsTextSelectable,
                                onIsTextSelectableChange = viewModel::updateGlobalIsTextSelectable,

                                modelDropdownExpanded = uiState.modelDropdownExpanded,
                                onModelDropdownExpandedChange = viewModel::setModelDropdownExpanded,
                                onChatHistoryImported = onChatHistoryImportedParent
                            )

                            1 -> if (hasActiveSession) SessionSettingsPage(
                                modelApiName = uiState.sessionModelApiName,
                                onModelApiNameChange = { newName ->
                                    viewModel.updateSessionModelApiName(newName)
                                    // 立即回调，因为会话设置的持久化依赖外部
                                    onUpdateCurrentSessionSettings(
                                        newName,
                                        uiState.sessionSystemPrompt,
                                        uiState.sessionModelSettings.copy(
                                            temperature = uiState.sessionModelSettings.temperature,
                                            frequencyPenalty = uiState.sessionModelSettings.frequencyPenalty,
                                            topP = uiState.sessionModelSettings.topP
                                        )
                                    )
                                },
                                systemPrompt = uiState.sessionSystemPrompt,
                                onSystemPromptChange = { newPrompt ->
                                    viewModel.updateSessionSystemPrompt(newPrompt)
                                    onUpdateCurrentSessionSettings(
                                        uiState.sessionModelApiName,
                                        newPrompt,
                                        uiState.sessionModelSettings.copy(
                                            temperature = uiState.sessionModelSettings.temperature,
                                            frequencyPenalty = uiState.sessionModelSettings.frequencyPenalty,
                                            topP = uiState.sessionModelSettings.topP
                                        )
                                    )
                                },
                                // 从 uiState.sessionModelSettings 中取值并传递对应的 ViewModel 更新方法
                                temp = uiState.sessionModelSettings.temperature,
                                onTempChange = { newTemp ->
                                    viewModel.updateSessionTemperature(newTemp)
                                    onUpdateCurrentSessionSettings(
                                        uiState.sessionModelApiName,
                                        uiState.sessionSystemPrompt,
                                        uiState.sessionModelSettings.copy(temperature = newTemp)
                                    )
                                },
                                freqP = uiState.sessionModelSettings.frequencyPenalty,
                                onFreqPChange = { newFreqP ->
                                    viewModel.updateSessionFrequencyPenalty(newFreqP)
                                    onUpdateCurrentSessionSettings(
                                        uiState.sessionModelApiName,
                                        uiState.sessionSystemPrompt,
                                        uiState.sessionModelSettings.copy(frequencyPenalty = newFreqP)
                                    )
                                },
                                topP = uiState.sessionModelSettings.topP,
                                onTopPChange = { newTopP ->
                                    viewModel.updateSessionTopP(newTopP)
                                    onUpdateCurrentSessionSettings(
                                        uiState.sessionModelApiName,
                                        uiState.sessionSystemPrompt,
                                        uiState.sessionModelSettings.copy(topP = newTopP)
                                    )
                                },
                                // SessionSettingsPage 不再需要 autoShowDialog 和 isTextSelectable，因为它们是全局的
                                modelDropdownExpanded = uiState.modelDropdownExpanded,
                                onModelDropdownExpandedChange = viewModel::setModelDropdownExpanded
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (uiState.currentPagerPage == 0) {
                    onSaveGlobalDefaults(
                        uiState.globalAutoSaveOnSwitch, uiState.globalAutoVerifyResponse,
                        uiState.globalAutoShowStreamingDialog, uiState.globalIsTextSelectable,
                        uiState.apiKey, uiState.apiUrl,
                        uiState.globalModelApiName, uiState.globalSystemPrompt,
                        uiState.globalModelSettings // 这个对象包含了 temp, freqP, topP
                    )
                } else if (hasActiveSession) {
                    // 对于会话设置，当用户修改时，onUpdateCurrentSessionSettings 已经实时更新了 ChatScreen 的 activeXXX 状态
                    // 所以这里的 "应用更改" 按钮主要是为了关闭对话框。
                    // ChatScreen 的 "保存会话" 按钮负责持久化这些 activeXXX 状态到 SessionMeta。
                    // 确保最新的修改通过 onUpdateCurrentSessionSettings 回调出去
                    onUpdateCurrentSessionSettings(
                        uiState.sessionModelApiName,
                        uiState.sessionSystemPrompt,
                        uiState.sessionModelSettings
                    )
                }
                onDismiss()
            }) { Text(if (pagerState.currentPage == 0 || !hasActiveSession) "保存全局" else "应用更改") } // 按钮文本根据页面变化
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}


// --- 新建的页面 Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSettingsPage(
    apiKey: String, onApiKeyChange: (String) -> Unit,
    apiUrl: String, onApiUrlChange: (String) -> Unit,
    modelApiName: String, onModelApiNameChange: (String) -> Unit,
    systemPrompt: String, onSystemPromptChange: (String) -> Unit,
    autoSave: Boolean, onAutoSaveChange: (Boolean) -> Unit,
    autoVerify: Boolean, onAutoVerifyChange: (Boolean) -> Unit,
    temp: Float, onTempChange: (Float) -> Unit,
    freqP: Float, onFreqPChange: (Float) -> Unit,
    topP: Float, onTopPChange: (Float) -> Unit,
    autoShowDialog: Boolean, onAutoShowDialogChange: (Boolean) -> Unit,
    isTextSelectable: Boolean, onIsTextSelectableChange: (Boolean) -> Unit,
    modelDropdownExpanded: Boolean, onModelDropdownExpandedChange: (Boolean) -> Unit,
    // 回调，用于在导入成功后通知 ChatScreen 刷新
    onChatHistoryImported: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- 文件选择器启动器 ---
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    val success = BackupRestoreManager.exportChatHistory(context, it)
                    if (success) {
                        Toast.makeText(context, "导出成功!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "导出失败。", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                coroutineScope.launch {
                    // 为简单起见，使用默认的 CREATE_COPY 策略，你可以后续添加选择对话框
                    val success = BackupRestoreManager.importChatHistory(
                        context,
                        it,
                        ImportConflictStrategy.CREATE_COPY
                    )
                    if (success) {
                        Toast.makeText(context, "导入成功! 会话列表将刷新。", Toast.LENGTH_LONG)
                            .show()
                        onChatHistoryImported() // 通知 ChatScreen 刷新
                    } else {
                        Toast.makeText(context, "导入失败。", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Column {
        ExpandableSettingSection(title = "API 设置", initiallyExpanded = false) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API 密钥") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                singleLine = true,
                placeholder = { Text("在此输入您的 API Key") }
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = onApiUrlChange, // 使用已有的回调
                    label = { Text("API 地址") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("例如：https://api.openai.com/v1/...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    // 直接调用 onApiUrlChange，将值设置为默认常量
                    onApiUrlChange(DEFAULT_API_URL)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.restore),
                        contentDescription = "恢复默认API地址"
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        ExpandableSettingSection(title = "数据管理", initiallyExpanded = false) {
            Button(
                onClick = {
                    // 生成默认文件名，例如 KlusterAIChats_YYYYMMDD_HHMM.zip
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                    val timestamp = sdf.format(Date())
                    exportLauncher.launch("KlusterAIChats_Backup_$timestamp.zip")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("导出所有聊天记录")
            }
            Button(
                onClick = { importLauncher.launch("application/zip") }, // 限制文件类型为ZIP
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("导入聊天记录")
            }
            Text(
                "导入会话时，如果会话ID已存在，将创建副本。导入后可能需要重启应用或刷新列表。",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }
        ExpandableSettingSection(title = "应用行为", initiallyExpanded = false) {
            SwitchSettingItem(
                title = "自动保存",
                descriptionOn = "切换/新建会话或退出应用时，将自动保存未保存的更改。",
                descriptionOff = "离开当前聊天（切换/新建/退出）时，若有未保存更改将提示操作。",
                checked = autoSave,
                onCheckedChange = onAutoSaveChange
            )
            HorizontalDivider()
            SwitchSettingItem(
                title = "可靠性审查",
                descriptionOn = "每次模型回复后将自动调用审查模型。",
                descriptionOff = "模型回复后不自动进行审查。",
                checked = autoVerify,
                onCheckedChange = onAutoVerifyChange
            )
        }
        ExpandableSettingSection(title = "界面体验", initiallyExpanded = false) {
            SwitchSettingItem(
                title = "自动显示实时回复框",
                descriptionOn = "新回复时会自动弹出。",
                descriptionOff = "新回复时默认隐藏。",
                checked = autoShowDialog,
                onCheckedChange = onAutoShowDialogChange
            )
            SwitchSettingItem(
                title = "启用消息文本选择",
                descriptionOn = "可以长按选择和复制消息文本。",
                descriptionOff = "消息文本不可选。",
                checked = isTextSelectable,
                onCheckedChange = onIsTextSelectableChange
            )
        }
        ExpandableSettingSection(title = "默认模型与提示", initiallyExpanded = false) {
            // ... (模型选择和系统提示的 UI，使用传入的 onXXXChange 回调)
            DefaultModelAndPromptSettings(
                modelApiName = modelApiName,
                onModelApiNameChange = onModelApiNameChange,
                systemPrompt = systemPrompt,
                onSystemPromptChange = onSystemPromptChange,
                modelDropdownExpanded = modelDropdownExpanded,
                onModelDropdownExpandedChange = onModelDropdownExpandedChange
            )
        }
        ExpandableSettingSection(title = "默认高级参数", initiallyExpanded = false) {
            // ... (温度、TopP、频率惩罚的 SettingSliderItem，使用传入的 onXXXChange 回调)
            DefaultAdvancedParamsSettings(
                temp = temp, onTempChange = onTempChange,
                topP = topP, onTopPChange = onTopPChange,
                freqP = freqP, onFreqPChange = onFreqPChange
            )
        }
        Text(
            "这些是新会话的默认设置，或在没有特定会话设置时使用。",
            style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSettingsPage(
    modelApiName: String, onModelApiNameChange: (String) -> Unit,
    systemPrompt: String, onSystemPromptChange: (String) -> Unit,
    temp: Float, onTempChange: (Float) -> Unit,
    freqP: Float, onFreqPChange: (Float) -> Unit,
    topP: Float, onTopPChange: (Float) -> Unit,
    modelDropdownExpanded: Boolean, onModelDropdownExpandedChange: (Boolean) -> Unit
) {
    Column {
        ExpandableSettingSection(title = "模型与提示", initiallyExpanded = true) {
            // ... (与 GlobalSettingsPage 类似的 UI，但绑定的是会话特定状态和回调)
            DefaultModelAndPromptSettings( // 复用 UI 结构
                modelApiName = modelApiName,
                onModelApiNameChange = onModelApiNameChange,
                systemPrompt = systemPrompt,
                onSystemPromptChange = onSystemPromptChange,
                modelDropdownExpanded = modelDropdownExpanded,
                onModelDropdownExpandedChange = onModelDropdownExpandedChange
            )
        }
        ExpandableSettingSection(
            title = "高级参数",
            initiallyExpanded = false
        ) { // 会话的高级参数默认展开
            DefaultAdvancedParamsSettings( // 复用 UI 结构
                temp = temp, onTempChange = onTempChange,
                topP = topP, onTopPChange = onTopPChange,
                freqP = freqP, onFreqPChange = onFreqPChange
            )
        }
        Text(
            "这些设置将应用于当前会话，并在您“保存会话”时持久化。",
            style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
    }
}

// --- 可复用的设置UI片段 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultModelAndPromptSettings(
    modelApiName: String, onModelApiNameChange: (String) -> Unit,
    systemPrompt: String, onSystemPromptChange: (String) -> Unit,
    modelDropdownExpanded: Boolean, onModelDropdownExpandedChange: (Boolean) -> Unit // 传入状态和回调
) {
    Text(
        "模型选择:",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp)
    )
    ExposedDropdownMenuBox(
        expanded = modelDropdownExpanded, // 使用传入的状态
        onExpandedChange = onModelDropdownExpandedChange, // 使用传入的回调
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = availableModels.find { it.apiName == modelApiName }?.displayName ?: "选择模型",
            onValueChange = {}, readOnly = true, label = { Text("当前模型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = modelDropdownExpanded,
            onDismissRequest = { onModelDropdownExpandedChange(false) }) {
            availableModels.forEach { model ->
                DropdownMenuItem(text = { Text(model.displayName) }, onClick = {
                    onModelApiNameChange(model.apiName)
                    onModelDropdownExpandedChange(false)
                })
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedTextField(
        value = systemPrompt,
        onValueChange = onSystemPromptChange,
        label = { Text("系统提示内容") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 200.dp)
            .padding(bottom = 8.dp),
        minLines = 3,
        maxLines = 8
    )
}

@Composable
fun DefaultAdvancedParamsSettings(
    temp: Float, onTempChange: (Float) -> Unit,
    topP: Float, onTopPChange: (Float) -> Unit,
    freqP: Float, onFreqPChange: (Float) -> Unit
) {
    SettingSliderItem(
        label = "模型温度", value = temp, valueRange = 0.0f..2.0f,
        steps = ((2.0f - 0.0f) / 0.1f).toInt() - 1, onValueChange = onTempChange,
        valueLabelFormat = "%.1f", description = "较低值更确定，较高值更具创造性。"
    )
    SettingSliderItem(
        label = "Top-P (核心采样)", value = topP, valueRange = 0.01f..1.0f,
        steps = ((1.0f - 0.01f) / 0.01f).toInt() - 1, onValueChange = onTopPChange,
        valueLabelFormat = "%.2f", description = "控制输出多样性，1.0 表示不限制。"
    )
    SettingSliderItem(
        label = "频率惩罚", value = freqP, valueRange = -2.0f..2.0f,
        steps = ((2.0f - (-2.0f)) / 0.1f).toInt() - 1, onValueChange = onFreqPChange,
        valueLabelFormat = "%.1f", description = "正值减少重复，负值鼓励重复。"
    )
}

@Composable
fun ExpandableSettingSection(
    title: String,
    initiallyExpanded: Boolean = false, // 是否默认展开
    content: @Composable () -> Unit
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        label = "rotation"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 12.dp), // 调整标题的垂直内边距
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium, // 使用中等标题样式
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown, // 始终向下，通过旋转实现效果
                contentDescription = if (isExpanded) "折叠 $title" else "展开 $title",
                modifier = Modifier.rotate(rotationAngle)
            )
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier.padding(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 8.dp
                )
            ) { // 给内容一些内边距
                content()
            }
        }
        HorizontalDivider() // 每个部分之间的分割线
    }
}


// 一个辅助 Composable 来标准化 Slider 的展示
@Composable
fun SettingSliderItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabelFormat: String,
    description: String
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "$label: ${String.format(Locale.US, valueLabelFormat, value)}",
            style = MaterialTheme.typography.titleSmall // 使用 titleSmall 保持一致
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 0.dp, bottom = 0.dp) // 调整 Slider 上下内边距
        )
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    descriptionOn: String, // 选中时的描述
    descriptionOff: String, // 未选中时的描述
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(vertical = 8.dp)) { // 给整个条目一些垂直内边距
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) } // 点击整行都可以切换状态
                .padding(vertical = 4.dp), // 标题行本身的内边距
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall, // 保持与 Slider 标签一致
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            )
        }
        Text(
            text = if (checked) descriptionOn else descriptionOff,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp) // 描述文本的内边距
        )
    }
}