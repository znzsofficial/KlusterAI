package com.nekolaska.klusterai.viewmodels

import androidx.lifecycle.ViewModel
import com.nekolaska.klusterai.data.ModelSettings // 确保导入你的 ModelSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// 定义一个 UI State 数据类来持有所有设置项的当前状态
data class SettingsUiState(
    // 全局设置的当前编辑值
    val apiKey: String = "",
    val apiUrl: String = "",
    val globalModelApiName: String = "",
    val globalSystemPrompt: String = "",
    val globalModelSettings: ModelSettings = ModelSettings.DEFAULT,
    val globalAutoSaveOnSwitch: Boolean = false,
    val globalAutoVerifyResponse: Boolean = true,
    val globalAutoShowStreamingDialog: Boolean = true,
    val globalIsTextSelectable: Boolean = true,

    // 当前会话设置的当前编辑值
    val sessionModelApiName: String = "", // 会用全局或实际会话值初始化
    val sessionSystemPrompt: String = "",
    val sessionModelSettings: ModelSettings = ModelSettings.DEFAULT,

    val modelDropdownExpanded: Boolean = false,
    val currentPagerPage: Int = 0 // 跟踪 Pager 的当前页面，用于保存逻辑
)

class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // 初始化 ViewModel，通常在 ChatScreen 创建 ViewModel 时调用一次
    fun initializeSettings(
        // 全局值
        gApiKey: String, gApiUrl: String, gModelName: String, gSystemPrompt: String,
        gModelSettings: ModelSettings, gAutoSave: Boolean, gAutoVerify: Boolean,
        gAutoShowDialog: Boolean, gIsTextSelectable: Boolean,
        // 会话值 (如果存在)
        hasActiveSession: Boolean,
        sModelName: String?, sSystemPrompt: String?, sModelSettings: ModelSettings?
    ) {
        _uiState.update { currentState ->
            val initialSessionSettings = sModelSettings ?: gModelSettings
            val initialSessionModelName = sModelName ?: gModelName
            val initialSessionSystemPrompt = sSystemPrompt ?: gSystemPrompt

            currentState.copy(
                apiKey = gApiKey,
                apiUrl = gApiUrl,
                globalModelApiName = gModelName,
                globalSystemPrompt = gSystemPrompt,
                globalModelSettings = gModelSettings,
                globalAutoSaveOnSwitch = gAutoSave,
                globalAutoVerifyResponse = gAutoVerify,
                globalAutoShowStreamingDialog = gAutoShowDialog,
                globalIsTextSelectable = gIsTextSelectable,

                sessionModelApiName = initialSessionModelName,
                sessionSystemPrompt = initialSessionSystemPrompt,
                sessionModelSettings = initialSessionSettings,
                currentPagerPage = if (hasActiveSession) currentState.currentPagerPage else 0 // 如果没会话，默认全局页
            )
        }
    }

    // --- 更新全局设置的方法 ---
    fun updateApiKey(newApiKey: String) {
        _uiState.update { it.copy(apiKey = newApiKey) }
    }
    fun updateApiUrl(newApiUrl: String) {
        _uiState.update { it.copy(apiUrl = newApiUrl) }
    }
    // ... 为所有全局设置创建类似的 update 方法 ...
    fun updateGlobalModelApiName(name: String) { _uiState.update { it.copy(globalModelApiName = name) } }
    fun updateGlobalSystemPrompt(prompt: String) { _uiState.update { it.copy(globalSystemPrompt = prompt) } }
    fun updateGlobalAutoSave(value: Boolean) { _uiState.update { it.copy(globalAutoSaveOnSwitch = value) } }
    fun updateGlobalAutoVerify(value: Boolean) { _uiState.update { it.copy(globalAutoVerifyResponse = value) } }
    fun updateGlobalAutoShowDialog(value: Boolean) { _uiState.update { it.copy(globalAutoShowStreamingDialog = value) } }
    fun updateGlobalIsTextSelectable(value: Boolean) { _uiState.update { it.copy(globalIsTextSelectable = value) } }
    fun updateGlobalTemperature(value: Float) { _uiState.update { it.copy(globalModelSettings = it.globalModelSettings.copy(temperature = value)) } }
    fun updateGlobalFrequencyPenalty(value: Float) { _uiState.update { it.copy(globalModelSettings = it.globalModelSettings.copy(frequencyPenalty = value)) } }
    fun updateGlobalTopP(value: Float) { _uiState.update { it.copy(globalModelSettings = it.globalModelSettings.copy(topP = value)) } }


    // --- 更新会话特定设置的方法 ---
    fun updateSessionModelApiName(name: String) { _uiState.update { it.copy(sessionModelApiName = name) } }
    fun updateSessionSystemPrompt(prompt: String) { _uiState.update { it.copy(sessionSystemPrompt = prompt) } }
    fun updateSessionTemperature(value: Float) { _uiState.update { it.copy(sessionModelSettings = it.sessionModelSettings.copy(temperature = value)) } }
    fun updateSessionFrequencyPenalty(value: Float) { _uiState.update { it.copy(sessionModelSettings = it.sessionModelSettings.copy(frequencyPenalty = value)) } }
    fun updateSessionTopP(value: Float) { _uiState.update { it.copy(sessionModelSettings = it.sessionModelSettings.copy(topP = value)) } }
    // 注意：会话特定的 autoShowDialog 和 isTextSelectable 已被移除，它们现在是全局的

    // --- 其他 UI 状态更新 ---
    fun setModelDropdownExpanded(isExpanded: Boolean) {
        _uiState.update { it.copy(modelDropdownExpanded = isExpanded) }
    }
    fun setCurrentPagerPage(page: Int) {
        _uiState.update { it.copy(currentPagerPage = page) }
    }
}