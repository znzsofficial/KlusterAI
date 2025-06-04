package com.nekolaska.klusterai.ui.components

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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextAlign
import com.nekolaska.klusterai.data.ModelSettings
import com.nekolaska.klusterai.data.availableModels
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isGlobalSettingsMode: Boolean,
    currentApiKey: String,
    currentSelectedModelApiName: String,
    currentSystemPrompt: String,
    currentAutoSaveOnSwitch: Boolean,
    currentModelSettings: ModelSettings,
    onSaveGlobalDefaults: (
        autoSave: Boolean, apiKey: String, modelApiName: String, systemPrompt: String, modelSettings: ModelSettings
    ) -> Unit,
    onSaveSessionSpecific: (modelApiName: String, systemPrompt: String, modelSettings: ModelSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var autoSaveOnSwitchState by remember(currentAutoSaveOnSwitch, isGlobalSettingsMode) {
        mutableStateOf(if (isGlobalSettingsMode) currentAutoSaveOnSwitch else false)
    }
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

    val defaultExpansionState = false // 高级参数默认折叠

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isGlobalSettingsMode) "全局默认设置" else "当前会话设置") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp) // 给整个可滚动内容区域统一的水平内边距
            ) {

                if (isGlobalSettingsMode) {
                    ExpandableSettingSection(title = "API 设置", initiallyExpanded = true) {
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { if (it.length <= 100) apiKeyInput = it },
                            label = { Text("API 密钥 (全局)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp),
                            singleLine = true,
                            placeholder = { Text("在此输入您的 API Key") }
                        )
                    }

                    ExpandableSettingSection(title = "应用行为", initiallyExpanded = false) {
                        SwitchSettingItem(
                            title = "离开当前聊天时自动保存",
                            descriptionOn = "切换/新建会话或退出应用时，将自动保存未保存的更改。",
                            descriptionOff = "离开当前聊天（切换/新建/退出）时，若有未保存更改将提示操作。",
                            checked = autoSaveOnSwitchState,
                            onCheckedChange = { autoSaveOnSwitchState = it },
                        )
                    }
                }

                ExpandableSettingSection(title = "模型与提示", initiallyExpanded = true) {
                    ExposedDropdownMenuBox(
                        expanded = modelDropdownExpanded,
                        onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded },
                        modifier = Modifier.padding(vertical = 4.dp) // 给Box一些垂直内边距
                    ) {
                        OutlinedTextField(
                            value = availableModels.find { it.apiName == selectedModelApiNameState }?.displayName
                                ?: "选择模型",
                            onValueChange = {}, // 因为 readOnly，所以为空
                            readOnly = true,
                            label = { Text("当前模型") }, // TextField 内部的 label
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable) // 重要：用于只读 TextField 作为锚点
                                .fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors() // 使用默认颜色或自定义
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
                        onValueChange = { if (it.length <= 6000) systemPromptInput = it },
                        label = { Text("系统提示内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp)
                            .padding(bottom = 8.dp),
                        minLines = 3, maxLines = 8,
                        placeholder = { Text(if (isGlobalSettingsMode) "输入全局默认系统提示..." else "输入当前会话的系统提示...") }
                    )

                    // UI 体验设置
                    SwitchSettingItem(
                        title = "自动显示实时回复框",
                        descriptionOn = "新回复时会自动弹出。",
                        descriptionOff = "新回复时默认隐藏。",
                        checked = autoShowDialogState,
                        onCheckedChange = { autoShowDialogState = it },
                    )
                    SwitchSettingItem(
                        title = "启用消息文本选择",
                        descriptionOn = "可以长按选择和复制消息文本。",
                        descriptionOff = "消息文本不可选。",
                        checked = isTextSelectableState,
                        onCheckedChange = { isTextSelectableState = it },
                    )
                }

                ExpandableSettingSection(
                    title = "高级参数调整",
                    initiallyExpanded = defaultExpansionState
                ) {
                    SettingSliderItem(
                        label = "模型温度", value = temperatureState,
                        valueRange = 0.0f..2.0f, steps = ((2.0f - 0.0f) / 0.1f).toInt() - 1,
                        onValueChange = { temperatureState = it }, valueLabelFormat = "%.1f",
                        description = "较低值更确定，较高值更具创造性。"
                    )
                    SettingSliderItem(
                        label = "Top-P (核心采样)", value = topPState,
                        valueRange = 0.01f..1.0f, steps = ((1.0f - 0.01f) / 0.01f).toInt() - 1,
                        onValueChange = { topPState = it }, valueLabelFormat = "%.2f",
                        description = "控制输出多样性，1.0 表示不限制。"
                    )
                    SettingSliderItem(
                        label = "频率惩罚", value = frequencyPenaltyState,
                        valueRange = -2.0f..2.0f, steps = ((2.0f - (-2.0f)) / 0.1f).toInt() - 1,
                        onValueChange = { frequencyPenaltyState = it }, valueLabelFormat = "%.1f",
                        description = "正值减少重复，负值鼓励重复。"
                    )
                }

                if (!isGlobalSettingsMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "注意：此处的更改将应用于当前打开的会话，并在您手动保存会话后持久化。",
                        style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updatedModelSettings = ModelSettings(
                    temperature = temperatureState,
                    frequencyPenalty = frequencyPenaltyState,
                    autoShowStreamingDialog = autoShowDialogState,
                    topP = topPState,
                    isTextSelectableInBubble = isTextSelectableState
                )
                if (isGlobalSettingsMode) {
                    onSaveGlobalDefaults(
                        autoSaveOnSwitchState,
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