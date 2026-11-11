package com.xiaoliu.webporduce.ai;

import com.xiaoliu.webporduce.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/**
 * 首版统一接入的三类协议。OpenAI 兼容协议可以覆盖 DeepSeek、Moonshot、通义千问、
 * 智谱、OpenRouter、Groq、Ollama、vLLM 等大多数服务。
 */
public enum AiProviderType {
    OPENAI_COMPATIBLE(
            "OpenAI 兼容",
            "https://api.openai.com/v1",
            "gpt-4o-mini",
            "适用于 OpenAI、DeepSeek、Moonshot、通义千问、智谱、OpenRouter、Groq、Ollama、vLLM 等服务"
    ),
    ANTHROPIC(
            "Anthropic",
            "https://api.anthropic.com/v1",
            "claude-3-5-sonnet-latest",
            "使用 Anthropic Messages API，支持 Claude 系列模型"
    ),
    GEMINI(
            "Google Gemini",
            "https://generativelanguage.googleapis.com/v1beta",
            "gemini-2.0-flash",
            "使用 Gemini generateContent API"
    );

    private final String label;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final String description;

    AiProviderType(String label, String defaultBaseUrl, String defaultModel, String description) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String description() {
        return description;
    }

    public static AiProviderType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_PROVIDER_INVALID", "请选择 AI 服务商");
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AI_PROVIDER_INVALID", "不支持的 AI 服务商");
        }
    }
}
