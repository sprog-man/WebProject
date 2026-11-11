package com.xiaoliu.webporduce.ai;

import java.time.Instant;
import java.util.UUID;

public record AiConfigResponse(
        UUID id,
        String name,
        String providerType,
        String baseUrl,
        String model,
        String keyHint,
        boolean hasApiKey,
        boolean defaultConfig,
        Instant createdAt,
        Instant updatedAt
) {

    static AiConfigResponse from(AiConfig config) {
        return new AiConfigResponse(
                config.getId(),
                config.getName(),
                config.getProviderType(),
                config.getBaseUrl(),
                config.getModel(),
                config.getApiKeyHint(),
                config.getApiKeyHint() != null && !config.getApiKeyHint().isBlank(),
                config.isDefaultConfig(),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
