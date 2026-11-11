package com.xiaoliu.webporduce.ai;

/**
 * 服务端内部使用的临时运行配置。API Key 只在内存中短暂存在，不会返回给前端。
 */
public record AiRuntimeConfig(
        AiProviderType type,
        String name,
        String baseUrl,
        String apiKey,
        String model
) {

    public AiRuntimeConfig {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        model = model == null ? "" : model.trim();
        name = name == null || name.isBlank() ? type.label() : name.trim();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }
}
