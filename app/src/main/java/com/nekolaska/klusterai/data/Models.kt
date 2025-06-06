package com.nekolaska.klusterai.data

// --- Model Data Definition ---
data class ModelInfo(val displayName: String, val apiName: String)

val availableModels = listOf(
    ModelInfo("DeepSeek-R1-0528", "deepseek-ai/DeepSeek-R1-0528"),
    ModelInfo("DeepSeek-V3-0324", "deepseek-ai/DeepSeek-V3-0324"),
    ModelInfo("DeepSeek-R1", "deepseek-ai/DeepSeek-R1"),
    //ModelInfo("Kluster 可靠性检查", "klusterai/verify-reliability"),
    ModelInfo("Qwen3-235B-A22B", "Qwen/Qwen3-235B-A22B-FP8"),
    ModelInfo("Qwen2.5-VL 7B", "Qwen/Qwen2.5-VL-7B-Instruct"),
    ModelInfo("Gemma 3 27B", "google/gemma-3-27b-it"),
    ModelInfo("Meta Llama 3.1 8B", "klusterai/Meta-Llama-3.1-8B-Instruct-Turbo"),
    ModelInfo("Meta Llama 3.3 70B", "klusterai/Meta-Llama-3.3-70B-Instruct-Turbo"),
    ModelInfo("Meta Llama 4 Maverick", "meta-llama/Llama-4-Maverick-17B-128E-Instruct-FP8"),
    ModelInfo("Meta Llama 4 Scout", "meta-llama/Llama-4-Scout-17B-16E-Instruct"),
    ModelInfo("Mistral NeMo", "mistralai/Mistral-Nemo-Instruct-2407"),
)
val DEFAULT_MODEL_API_NAME =
    availableModels.firstOrNull()?.apiName ?: "DeepSeek-R1-0528"