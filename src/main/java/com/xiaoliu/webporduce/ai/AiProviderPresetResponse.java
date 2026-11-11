package com.xiaoliu.webporduce.ai;

public record AiProviderPresetResponse(
        String type,
        String label,
        String defaultBaseUrl,
        String defaultModel,
        String description
) {
}
