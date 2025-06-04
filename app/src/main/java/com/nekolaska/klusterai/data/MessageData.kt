package com.nekolaska.klusterai.data

// --- 消息数据类 ---
data class MessageData(
    val role: String,
    val content: String,
    val thinkContent: String? = null,
    val id: Long = System.nanoTime() // 使用纳秒保证ID的独特性
)