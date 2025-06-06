package com.nekolaska.klusterai.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

object ReasoningSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Reasoning", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        // 序列化时，我们总是期望它是一个 String
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonInput = decoder as? JsonDecoder ?: throw IllegalStateException("This serializer can only be used with Json")
        val element = jsonInput.decodeJsonElement() // 解码为通用的 JsonElement

        return when (element) {
            is JsonPrimitive -> if (element.isString) element.content else element.toString()
            is JsonArray -> element.jsonArray.joinToString(separator = "\n- ", prefix = "- ") {
                if (it is JsonPrimitive && it.isString) it.content else it.toString()
            }
            else -> element.toString()
        }
    }
}

@Serializable
data class VerificationResult(
    @SerialName("REASONING")
    @Serializable(with = ReasoningSerializer::class) // 应用自定义序列化器
    val reasoning: String,
    @SerialName("HALLUCINATION")
    val hallucination: String
)