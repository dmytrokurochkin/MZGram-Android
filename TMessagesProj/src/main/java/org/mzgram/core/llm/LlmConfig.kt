package org.mzgram.core.llm

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.mzgram.core.CoreConfig
import org.mzgram.core.config.ConfigItem
import org.mzgram.core.llm.net.VertexGeminiClient
import org.mzgram.core.llm.preset.PresetRegistry
import org.mzgram.core.llm.utils.UrlNormalizer
import org.mzgram.core.translate.Translator
import org.mzgram.ext.ExtConfig

object LlmConfig {

    @JvmStatic
    fun getDefaultModelName(preset: Int): String {
        if (preset == PresetRegistry.GOOGLE_AGENT_PLATFORM) {
            return VertexGeminiClient.MODELS[0]
        }
        return getString(PresetRegistry.getDefaultModelResId(preset))
    }

    @JvmStatic
    fun getSavedModelName(preset: Int): String {
        val value = when (preset) {
            PresetRegistry.OPENAI -> ExtConfig.llmProviderOpenAIModel.String()
            PresetRegistry.GOOGLE_AI_STUDIO -> ExtConfig.llmProviderGeminiModel.String()
            PresetRegistry.GROQ -> ExtConfig.llmProviderGroqModel.String()
            PresetRegistry.DEEPSEEK -> ExtConfig.llmProviderDeepSeekModel.String()
            PresetRegistry.XAI -> ExtConfig.llmProviderXAIModel.String()
            PresetRegistry.CEREBRAS -> ExtConfig.llmProviderCerebrasModel.String()
            PresetRegistry.OLLAMA_CLOUD -> ExtConfig.llmProviderOllamaCloudModel.String()
            PresetRegistry.OPENROUTER -> ExtConfig.llmProviderOpenRouterModel.String()
            PresetRegistry.VERCEL_AI_GATEWAY -> ExtConfig.llmProviderVercelAIGatewayModel.String()
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> ExtConfig.llmProviderVertexModel.String()
            else -> ExtConfig.llmModelName.String()
        }
        return value?.trim() ?: ""
    }

    @JvmStatic
    fun setSavedModelName(preset: Int, model: String?) {
        val value = model?.trim() ?: ""
        when (preset) {
            PresetRegistry.OPENAI -> ExtConfig.llmProviderOpenAIModel.setConfigString(value)
            PresetRegistry.GOOGLE_AI_STUDIO -> ExtConfig.llmProviderGeminiModel.setConfigString(value)
            PresetRegistry.GROQ -> ExtConfig.llmProviderGroqModel.setConfigString(value)
            PresetRegistry.DEEPSEEK -> ExtConfig.llmProviderDeepSeekModel.setConfigString(value)
            PresetRegistry.XAI -> ExtConfig.llmProviderXAIModel.setConfigString(value)
            PresetRegistry.CEREBRAS -> ExtConfig.llmProviderCerebrasModel.setConfigString(value)
            PresetRegistry.OLLAMA_CLOUD -> ExtConfig.llmProviderOllamaCloudModel.setConfigString(value)
            PresetRegistry.OPENROUTER -> ExtConfig.llmProviderOpenRouterModel.setConfigString(value)
            PresetRegistry.VERCEL_AI_GATEWAY -> ExtConfig.llmProviderVercelAIGatewayModel.setConfigString(value)
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> ExtConfig.llmProviderVertexModel.setConfigString(value)
            else -> ExtConfig.llmModelName.setConfigString(value)
        }
    }

    @JvmStatic
    fun getEffectiveModelName(preset: Int): String {
        val saved = getSavedModelName(preset)
        return saved.ifBlank {
            getDefaultModelName(preset)
        }
    }

    @JvmStatic
    fun getEffectiveBaseUrl(preset: Int): String {
        return if (preset == PresetRegistry.CUSTOM) {
            val userUrl = ExtConfig.llmApiUrl.String().trim()
            userUrl.ifEmpty {
                getString(R.string.LlmApiUrlDefault)
            }
        } else {
            PresetRegistry.getPresetBaseUrl(preset).orEmpty()
        }
    }

    @JvmStatic
    fun setSavedCustomBaseUrl(baseUrl: String?) {
        val value = UrlNormalizer.normalizeBaseUrl(baseUrl)
        ExtConfig.llmApiUrl.setConfigString(value)
    }

    @JvmStatic
    fun getApiKeyConfigItem(preset: Int): ConfigItem {
        return when (preset) {
            PresetRegistry.OPENAI -> ExtConfig.llmProviderOpenAIKey
            PresetRegistry.GOOGLE_AI_STUDIO -> ExtConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> ExtConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> ExtConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> ExtConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> ExtConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> ExtConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> ExtConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> ExtConfig.llmProviderVercelAIGatewayKey
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> ExtConfig.llmProviderVertexKey
            else -> ExtConfig.llmApiKey
        }
    }

    @JvmStatic
    fun getFirstApiKey(preset: Int): String? {
        val raw = getApiKeyConfigItem(preset).String()?.trim()
        if (raw.isNullOrBlank()) {
            return null
        }
        return raw.split(",")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    @JvmStatic
    fun isLLMTranslatorAvailable(): Boolean {
        val llmProvider = ExtConfig.llmProviderPreset.Int()
        val keyConfig = when (llmProvider) {
            PresetRegistry.OPENAI -> ExtConfig.llmProviderOpenAIKey
            PresetRegistry.GOOGLE_AI_STUDIO -> ExtConfig.llmProviderGeminiKey
            PresetRegistry.GROQ -> ExtConfig.llmProviderGroqKey
            PresetRegistry.DEEPSEEK -> ExtConfig.llmProviderDeepSeekKey
            PresetRegistry.XAI -> ExtConfig.llmProviderXAIKey
            PresetRegistry.CEREBRAS -> ExtConfig.llmProviderCerebrasKey
            PresetRegistry.OLLAMA_CLOUD -> ExtConfig.llmProviderOllamaCloudKey
            PresetRegistry.OPENROUTER -> ExtConfig.llmProviderOpenRouterKey
            PresetRegistry.VERCEL_AI_GATEWAY -> ExtConfig.llmProviderVercelAIGatewayKey
            PresetRegistry.GOOGLE_AGENT_PLATFORM -> ExtConfig.llmProviderVertexKey
            else -> ExtConfig.llmApiKey
        }
        return keyConfig.String().isNotEmpty()
    }

    @JvmStatic
    fun llmIsDefaultProvider(): Boolean {
        return CoreConfig.translationProvider.Int() == Translator.providerLLMTranslator
    }
}
