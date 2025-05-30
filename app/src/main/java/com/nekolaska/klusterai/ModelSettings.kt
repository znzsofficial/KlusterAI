package com.nekolaska.klusterai

import kotlinx.serialization.Serializable

@Serializable
data class ModelSettings(
    val temperature: Float = 0.7f,
    val frequencyPenalty: Float = 0.0f,
    val autoShowStreamingDialog: Boolean = true,
    val topP: Float = 1.0f,
) {
    companion object {
        // 提供一个默认实例，方便使用
        val DEFAULT = ModelSettings()
    }
}