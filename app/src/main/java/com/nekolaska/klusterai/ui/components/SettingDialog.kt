package com.nekolaska.klusterai.ui.components

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.nekolaska.klusterai.ModelSettings
import com.nekolaska.klusterai.availableModels
import java.util.Locale

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isGlobalSettingsMode: Boolean,
    currentApiKey: String,
    currentSelectedModelApiName: String,
    currentSystemPrompt: String,
    currentModelSettings: ModelSettings,
    onSaveGlobalDefaults: (apiKey: String, modelApiName: String, systemPrompt: String, modelSettings: ModelSettings) -> Unit,
    onSaveSessionSpecific: (modelApiName: String, systemPrompt: String, modelSettings: ModelSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyInput by remember(currentApiKey, isGlobalSettingsMode) {
        mutableStateOf(if (isGlobalSettingsMode) currentApiKey else "")
    }
    var selectedModelApiNameState by remember(currentSelectedModelApiName) {
        mutableStateOf(currentSelectedModelApiName)
    }
    var systemPromptInput by remember(currentSystemPrompt) { mutableStateOf(currentSystemPrompt) }

    var autoShowDialogState by remember(currentModelSettings.autoShowStreamingDialog) {
        mutableStateOf(currentModelSettings.autoShowStreamingDialog)
    }
    var isTextSelectableState by remember(currentModelSettings.isTextSelectableInBubble) {
        mutableStateOf(currentModelSettings.isTextSelectableInBubble)
    }

    var temperatureState by remember(currentModelSettings.temperature) {
        mutableFloatStateOf(currentModelSettings.temperature)
    }
    var frequencyPenaltyState by remember(currentModelSettings.frequencyPenalty) {
        mutableFloatStateOf(currentModelSettings.frequencyPenalty)
    }
    var topPState by remember(currentModelSettings.topP) {
        mutableFloatStateOf(currentModelSettings.topP)
    }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    // 控制哪些部分默认展开
    val defaultExpansionState = false // 或者你可以根据 isGlobalSettingsMode 设置不同的默认值

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGlobalSettingsMode) "全局默认设置" else "当前会话设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                // API Key 设置 (仅全局模式)
                if (isGlobalSettingsMode) {
                    ExpandableSettingSection(
                        title = "API 设置",
                        initiallyExpanded = true
                    ) { // API Key 默认展开
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
                    }
                }

                // 模型和提示设置
                ExpandableSettingSection(
                    title = "模型与提示",
                    initiallyExpanded = true
                ) { // 模型和提示默认展开
                    Text(
                        "选择模型:",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        OutlinedTextField( // 保持之前的 ExposedDropdownMenuBox 逻辑
                            value = availableModels.find { it.apiName == selectedModelApiNameState }?.displayName
                                ?: "选择模型",
                            onValueChange = {}, readOnly = true, label = { Text("当前模型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                        ExposedDropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false }) {
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
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = systemPromptInput,
                        onValueChange = { if (it.length <= 6000) systemPromptInput = it },
                        label = { Text("系统提示内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .padding(bottom = 8.dp),
                        minLines = 3, maxLines = 8,
                        placeholder = { Text(if (isGlobalSettingsMode) "输入全局默认系统提示..." else "输入当前会话的系统提示...") }
                    )

                    // 自动显示流式对话框设置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoShowDialogState = !autoShowDialogState }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "自动显示实时回复框",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoShowDialogState,
                            onCheckedChange = { autoShowDialogState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    }
                    Text(
                        if (autoShowDialogState) "新回复时会自动弹出。" else "新回复时默认隐藏。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // 消息文本可选设置
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTextSelectableState = !isTextSelectableState }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "启用消息文本选择/复制",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isTextSelectableState,
                            onCheckedChange = { isTextSelectableState = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        )
                    }
                    Text(
                        if (isTextSelectableState) "可以长按选择和复制消息气泡中的文本。" else "消息气泡中的文本不可选。",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // 高级模型参数设置
                ExpandableSettingSection(
                    title = "高级参数调整",
                    initiallyExpanded = defaultExpansionState
                ) {
                    // 温度设置
                    Text(
                        "模型温度: ${String.format(Locale.US, "%.1f", temperatureState)}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Slider(
                        value = temperatureState,
                        onValueChange = { temperatureState = it },
                        valueRange = 0.0f..2.0f,
                        steps = ((2.0f - 0.0f) / 0.1f).toInt() - 1, // 每隔0.1
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text(
                        "较低值更确定，较高值更具创造性。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Top-P 设置
                    Text(
                        "Top-P (核心采样): ${
                            String.format(
                                Locale.US,
                                "%.2f",
                                topPState
                            )
                        }", // 显示两位小数
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = topPState,
                        onValueChange = { topPState = it },
                        valueRange = 0.01f..1.0f, // Top-P 通常在 0 到 1 之间，0.0 可能无效
                        steps = ((1.0f - 0.01f) / 0.01f).toInt() - 1, // 每隔 0.01，共 98 步 (0.01 到 1.00)
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        "控制输出多样性，1.0 表示不限制。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 频率惩罚设置
                    Text(
                        "频率惩罚: ${String.format(Locale.US, "%.1f", frequencyPenaltyState)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = frequencyPenaltyState,
                        onValueChange = { frequencyPenaltyState = it },
                        valueRange = -2.0f..2.0f,
                        steps = ((2.0f - (-2.0f)) / 0.1f).toInt() - 1, // 每隔0.1
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text("正值减少重复，负值鼓励重复。", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 会话特定设置的提示信息
                if (!isGlobalSettingsMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "注意：此处的更改将应用于当前打开的会话，并在您手动保存会话后持久化。",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 保存更改
                val updatedModelSettings = ModelSettings(
                    temperature = temperatureState,
                    frequencyPenalty = frequencyPenaltyState,
                    autoShowStreamingDialog = autoShowDialogState,
                    topP = topPState,
                    isTextSelectableInBubble = isTextSelectableState
                )
                if (isGlobalSettingsMode) {
                    onSaveGlobalDefaults(
                        apiKeyInput,
                        selectedModelApiNameState,
                        systemPromptInput,
                        updatedModelSettings
                    )
                } else {
                    onSaveSessionSpecific(
                        selectedModelApiNameState,
                        systemPromptInput,
                        updatedModelSettings
                    )
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large
    )
}