// SharedPreferencesUtils.kt
package com.nekolaska.klusterai

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.serialization.json.Json

object SharedPreferencesUtils {
    private const val PREFS_NAME = "klusterai_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_SELECTED_MODEL = "selected_model_api_name"
    private const val KEY_SYSTEM_PROMPT = "system_prompt"
    private const val KEY_GLOBAL_MODEL_SETTINGS = "global_model_settings"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true } // 需要 Json 实例

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveApiKey(context: Context, apiKey: String) {
        getPreferences(context).edit { putString(KEY_API_KEY, apiKey) }
    }

    fun loadApiKey(context: Context, defaultValue: String): String {
        return getPreferences(context).getString(KEY_API_KEY, defaultValue) ?: defaultValue
    }

    fun saveSelectedModel(context: Context, modelApiName: String) {
        getPreferences(context).edit { putString(KEY_SELECTED_MODEL, modelApiName) }
    }

    fun loadSelectedModel(context: Context, defaultValue: String): String {
        return getPreferences(context).getString(KEY_SELECTED_MODEL, defaultValue) ?: defaultValue
    }

    fun saveSystemPrompt(context: Context, systemPrompt: String) {
        getPreferences(context).edit { putString(KEY_SYSTEM_PROMPT, systemPrompt) }
    }

    fun loadSystemPrompt(context: Context, defaultValue: String): String {
        return getPreferences(context).getString(KEY_SYSTEM_PROMPT, defaultValue) ?: defaultValue
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