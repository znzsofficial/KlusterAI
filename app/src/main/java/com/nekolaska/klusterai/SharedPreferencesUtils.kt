package com.nekolaska.klusterai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nekolaska.klusterai.data.ModelSettings
import kotlinx.serialization.json.Json

object SharedPreferencesUtils {
    private const val PREFS_NAME = "klusterai_prefs"

    private const val KEY_API_URL = "api_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SELECTED_MODEL = "selected_model_api_name"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_GLOBAL_MODEL_SETTINGS = "global_model_settings"
    private const val KEY_AUTO_SAVE_ON_SWITCH = "auto_save_on_switch_session"
    private const val KEY_AUTO_VERIFY_RESPONSE = "auto_verify_response"
    private const val KEY_GLOBAL_AUTO_SHOW_STREAMING_DIALOG = "global_auto_show_streaming_dialog"
    private const val KEY_GLOBAL_IS_TEXT_SELECTABLE = "global_is_text_selectable"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true } // Json 实例

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun save(context: Context, key: String, value: String) {
        getPreferences(context).edit(true) {
            putString(key, value)
        }
    }

    private fun load(context: Context, key: String, defaultValue: String): String {
        return getPreferences(context).getString(key, defaultValue) ?: defaultValue
    }

    private fun save(context: Context, key: String, value: Boolean) {
        getPreferences(context).edit(true) {
            putBoolean(key, value)
        }
    }

    private fun load(context: Context, key: String, defaultValue: Boolean): Boolean {
        return getPreferences(context).getBoolean(key, defaultValue)
    }

    fun saveApiUrl(context: Context, apiUrl: String) = save(context, KEY_API_URL, apiUrl)

    fun loadApiUrl(context: Context, defaultValue: String): String =
        load(context, KEY_API_URL, defaultValue)

    fun saveApiKey(context: Context, apiKey: String) = save(context, KEY_API_KEY, apiKey)
    fun loadApiKey(context: Context, defaultValue: String): String =
        load(context, KEY_API_KEY, defaultValue)

    fun saveSelectedModel(context: Context, modelApiName: String) =
        save(context, KEY_SELECTED_MODEL, modelApiName)

    fun loadSelectedModel(context: Context, defaultValue: String) =
        load(context, KEY_SELECTED_MODEL, defaultValue)

    fun saveSystemPrompt(context: Context, systemPrompt: String) =
        save(context, KEY_SYSTEM_PROMPT, systemPrompt)

    fun loadSystemPrompt(context: Context, defaultValue: String) =
        load(context, KEY_SYSTEM_PROMPT, defaultValue)

    fun saveAutoSaveOnSwitchPreference(context: Context, autoSave: Boolean) =
        save(context, KEY_AUTO_SAVE_ON_SWITCH, autoSave)

    fun loadAutoSaveOnSwitchPreference(
        context: Context,
        defaultValue: Boolean = true
    ) = load(context, KEY_AUTO_SAVE_ON_SWITCH, defaultValue)

    fun saveAutoVerifyPreference(context: Context, autoVerify: Boolean) =
        save(context, KEY_AUTO_VERIFY_RESPONSE, autoVerify)

    fun loadAutoVerifyPreference(context: Context, defaultValue: Boolean = false) =
        load(context, KEY_AUTO_VERIFY_RESPONSE, defaultValue)

    fun saveGlobalAutoShowStreamingDialog(context: Context, autoShow: Boolean) =
        save(context, KEY_GLOBAL_AUTO_SHOW_STREAMING_DIALOG, autoShow)

    fun loadGlobalAutoShowStreamingDialog(context: Context, defaultValue: Boolean = true) =
        load(context, KEY_GLOBAL_AUTO_SHOW_STREAMING_DIALOG, defaultValue)

    fun saveGlobalIsTextSelectable(context: Context, selectable: Boolean) =
        save(context, KEY_GLOBAL_IS_TEXT_SELECTABLE, selectable)

    fun loadGlobalIsTextSelectable(context: Context, defaultValue: Boolean = false) =
        load(context, KEY_GLOBAL_IS_TEXT_SELECTABLE, defaultValue)

    fun saveGlobalModelSettings(context: Context, settings: ModelSettings) {
        val jsonString = json.encodeToString(settings)
        getPreferences(context).edit { putString(KEY_GLOBAL_MODEL_SETTINGS, jsonString) }
    }

    fun loadGlobalModelSettings(context: Context): ModelSettings {
        val jsonString = getPreferences(context).getString(KEY_GLOBAL_MODEL_SETTINGS, null)
        return if (jsonString != null) {
            try {
                json.decodeFromString<ModelSettings>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                ModelSettings.DEFAULT // 解析失败则返回默认值
            }
        } else {
            ModelSettings.DEFAULT // 未找到也返回默认值
        }
    }

}