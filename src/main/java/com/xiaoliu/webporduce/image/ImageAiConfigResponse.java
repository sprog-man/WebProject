package com.xiaoliu.webporduce.image;

import java.time.Instant;
import java.util.UUID;

public record ImageAiConfigResponse(
        UUID id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String keyHint,
        boolean hasApiKey,
        String defaultSize,
        String defaultQuality,
        boolean defaultConfig,
        Instant createdAt,
        Instant updatedAt
) {

    static ImageAiConfigResponse from(ImageAiConfig config) {
        return new ImageAiConfigResponse(
                config.getId(),
                config.getName(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getModel(),
                config.getApiKeyHint(),
                config.getApiKeyHint() != null && !config.getApiKeyHint().isBlank(),
                config.getDefaultSize(),
                config.getDefaultQuality(),
                config.isDefaultConfig(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
