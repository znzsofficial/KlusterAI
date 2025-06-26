package com.nekolaska.klusterai

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// --- 配置信息 ---
const val DEFAULT_API_KEY_FALLBACK =
    "YOUR_DEFAULT_API_KEY_HERE" // SharedPreferences中无密钥时的备用值
const val DEFAULT_API_URL = "https://api.kluster.ai/v1/chat/completions"
const val DEFAULT_SYSTEM_PROMPT = """你是一个多功能AI助手，名为 KlusterAI。
- **语气与风格:** 保持友好、专业且乐于助人。回答应清晰易懂。
- **准确性:** 尽力提供准确的信息。对于不确定的内容，请说明情况。
- **安全性:** 拒绝回答任何涉及危险、非法、不道德或仇恨内容的问题。
- **格式化:** 在适当的时候，请使用 Markdown 格式（例如列表、代码块、粗体）来增强回答的可读性。

请直接回答用户的问题。如果问题不够清晰，可以主动提问以获取更多信息，从而给出更精准的回复。"""

// 幻觉审查模型的常量
const val VERIFICATION_MODEL_NAME = "klusterai/verify-reliability"
const val VERIFICATION_USER_PROMPT = "请对提供的文本进行幻觉审查。务必以纯JSON格式返回结果，不能使用markdown代码块包裹，JSON包含'REASONING' (中文分析) 和 'HALLUCINATION' ('0'表示无幻觉, '1'表示检测到幻觉) 字段。不要添加任何额外文本"

// --- OkHttp 客户端 ---
val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(300, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(300, TimeUnit.SECONDS)
    .build()

// Json 实例，用于序列化请求体
// encodeDefaults = false: 如果属性值等于其在数据类中定义的默认值，则不序列化该属性。
// 对于可选参数，通常在数据类中将它们设为可空并默认值为 null。
// kotlinx.serialization 默认不序列化值为 null 的属性 (除非 explicitNulls = true)。
val jsonRequestBuilder = Json {
    prettyPrint = false // API 请求通常不需要美化打印
    encodeDefaults = false // 重要：如果属性值等于其默认值，则不序列化。
    ignoreUnknownKeys = true // 解析响应时仍然有用（虽然这里主要用于序列化）
    isLenient = true         // 增加对不严格JSON格式的容忍度（对请求体影响较小）
}