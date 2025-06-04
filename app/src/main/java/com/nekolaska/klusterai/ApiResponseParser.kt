package com.nekolaska.klusterai

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@Serializable
data class LlmChoice(
    // 字段名与 JSON 中的键名一致，或者使用 @SerialName 注解
    val message: LlmMessage? = null, // 可空，因为可能是 delta
    val delta: LlmDelta? = null     // 可空，因为可能是 message
    // 你可以添加其他 choice 中可能存在的字段，如果需要的话
    // val finish_reason: String? = null
)

@Serializable
data class LlmMessage(
    val content: String? = null // content 也可能是可空的或不存在的
    // val role: String? = null // 如果需要
)

@Serializable
data class LlmDelta(
    val content: String? = null // content 也可能是可空的或不存在的
    // val role: String? = null // 如果需要
)

@Serializable
data class LlmResponse( // 顶层 JSON 对象
    val choices: List<LlmChoice>? = null // choices 列表本身也可能是可空的或不存在的
    // 你可以添加其他顶层字段，如果需要的话
    // val id: String? = null,
    // val model: String? = null,
    // val created: Long? = null,
    // val usage: JsonElement? = null // 如果 usage 结构复杂或不固定
)


// 初始化 Json 解析器实例，可以配置它
// 将它作为顶层常量或 object 属性，以避免重复创建
private val jsonParser = Json {
    ignoreUnknownKeys = true // 忽略JSON中存在但数据类中没有的字段
    isLenient = true         // 允许一些不严格的JSON格式（例如，尾随逗号，如果API输出不标准）
    coerceInputValues = true // 如果JSON中的值类型与数据类不匹配（例如，期望Int但收到String "123"），尝试转换
}

fun extractContent(input: String): String? {
    try {
        // 输入的 input 可能是多行，每行是一个独立的 JSON 对象 (Server-Sent Events data part)
        // 我们需要逐行解析
        val lines = input.lines().filter { it.isNotBlank() }
        for (line in lines) {
            try {
                // 尝试将单行解析为 LlmResponse 对象
                val response = jsonParser.decodeFromString<LlmResponse>(line)

                // 从解析后的对象中提取 content
                response.choices?.firstOrNull()?.let { firstChoice ->
                    // 优先尝试 message.content，然后是 delta.content
                    val messageContent = firstChoice.message?.content
                    if (messageContent != null && messageContent.isNotBlank()) {
                        return messageContent
                    }

                    val deltaContent = firstChoice.delta?.content
                    if (deltaContent != null && deltaContent.isNotBlank()) {
                        return deltaContent
                    }
                    // 如果 deltaContent 是空字符串但存在，你可能仍然想返回它（取决于API行为）
                    // 例如，在流的末尾，content 可能是 ""
                    // if (firstChoice.delta != null && firstChoice.delta.content != null) return firstChoice.delta.content
                }
            } catch (_: SerializationException) {
                // 忽略单行解析错误，继续尝试下一行
                // println("单行JSON解析失败: $line, 错误: ${e.message}")
            } catch (_: Exception) {
                // 捕获其他可能的单行处理异常
            }
        }
        return null // 如果遍历所有行都没有找到 content
    } catch (e: Exception) {
        // 捕获处理整个 input 字符串时的顶层异常（例如 input.lines() 失败）
        println("提取内容时发生顶层错误: ${e.message}")
        return null
    }
}


private val THINK_REGEX = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
fun extractThinkSection(input: String): Pair<String?, String> {
    val match = THINK_REGEX.find(input)
    val thinkPart = match?.groupValues?.get(1)?.trim()
    val remainingPart = THINK_REGEX.replace(input, "").trim()
    return Pair(thinkPart, remainingPart)
}