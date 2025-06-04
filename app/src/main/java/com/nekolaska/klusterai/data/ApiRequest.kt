package com.nekolaska.klusterai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiRequestMessage(
    val role: String,
    val content: String
)

@Serializable
data class ApiRequestBody(
    val model: String,
    val messages: List<ApiRequestMessage>,
    val stream: Boolean? = null,
    val temperature: Float? = null, // 可选，让序列化器决定是否包含
    @SerialName("frequency_penalty") // JSON中是 snake_case
    val frequencyPenalty: Float? = null, // 可选
    @SerialName("top_p") // JSON中是 snake_case
    val topP: Float? = null, // 可选
    // @SerialName("max_tokens")
    // val maxTokens: Int? = null
)