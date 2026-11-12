package com.xiaoliu.webporduce.image;

public record ImageProviderPresetResponse(
        String type,
        String label,
        String defaultBaseUrl,
        String defaultModel,
        String description
) {
}
