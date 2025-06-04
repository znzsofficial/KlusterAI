// SharedPreferencesUtils.kt
package com.nekolaska.klusterai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.nekolaska.klusterai.data.ModelSettings
import kotlinx.serialization.json.Json

object SharedPreferencesUtils {
    private const val PREFS_NAME = "klusterai_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SELECTED_MODEL = "selected_model_api_name"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_GLOBAL_MODEL_SETTINGS = "global_model_settings"
    private const val KEY_AUTO_SAVE_ON_SWITCH = "auto_save_on_switch_session"
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

    fun saveAutoSaveOnSwitchPreference(context: Context, autoSave: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_AUTO_SAVE_ON_SWITCH, autoSave) }
    }

    fun loadAutoSaveOnSwitchPreference(context: Context, defaultValue: Boolean = false): Boolean { // 默认不自动保存
        return getPreferences(context).getBoolean(KEY_AUTO_SAVE_ON_SWITCH, defaultValue)
    }

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