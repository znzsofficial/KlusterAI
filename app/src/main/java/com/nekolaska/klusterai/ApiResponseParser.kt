package com.nekolaska.klusterai

import org.json.JSONObject

// --- 辅助函数 ---
fun extractContent(input: String): String? {
    return try {
        val lines = input.lines().filter { it.isNotBlank() }
        for (line in lines) {
            try {
                val jsonObject = JSONObject(line)
                if (jsonObject.has("choices")) {
                    val choicesArray = jsonObject.getJSONArray("choices")
                    if (choicesArray.length() > 0) {
                        val firstChoice = choicesArray.getJSONObject(0)
                        if (firstChoice.has("message")) {
                            return firstChoice.getJSONObject("message").getString("content")
                        } else if (firstChoice.has("delta") && firstChoice.getJSONObject("delta")
                                .has("content")
                        ) {
                            return firstChoice.getJSONObject("delta").getString("content")
                        }
                    }
                }
            } catch (_: Exception) { /* 忽略单行解析错误 */
            }
        }
        null
    } catch (e: Exception) {
        println("提取内容时出错: ${e.message}")
        null
    }
}

fun extractThinkSection(input: String): Pair<String?, String> {
    val regex = Regex("<think>(.*?)</think>", RegexOption.DOT_MATCHES_ALL)
    val match = regex.find(input)
    val thinkPart = match?.groupValues?.get(1)?.trim()
    val remainingPart = regex.replace(input, "").trim()
    return Pair(thinkPart, remainingPart)
}