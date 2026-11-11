package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final MockAiProvider mockAiProvider;
    private final AiProviderFactory providerFactory;
    private final AiConfigService configService;
    private final String providerName;
    private final String systemBaseUrl;
    private final String systemApiKey;
    private final String systemModel;

    public AiService(MockAiProvider mockAiProvider,
                     AiProviderFactory providerFactory,
                     AiConfigService configService,
                     @Value("${app.ai.provider:mock}") String providerName,
                     @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String systemBaseUrl,
                     @Value("${app.ai.deepseek.api-key:}") String systemApiKey,
                     @Value("${app.ai.deepseek.model:deepseek-chat}") String systemModel) {
        this.mockAiProvider = mockAiProvider;
        this.providerFactory = providerFactory;
        this.configService = configService;
        this.providerName = providerName;
        this.systemBaseUrl = systemBaseUrl;
        this.systemApiKey = systemApiKey == null ? "" : systemApiKey.trim();
        this.systemModel = systemModel;
    }

    public AiResult generate(UUID ownerId, String projectName, JsonNode currentSpec, String message) {
        return runWithFallback(ownerId, provider -> provider.generate(projectName, currentSpec, message));
    }

    public AiResult edit(UUID ownerId, JsonNode currentSpec, String message) {
        return runWithFallback(ownerId, provider -> provider.edit(currentSpec, message));
    }

    private AiResult runWithFallback(UUID ownerId, AiCall call) {
        AiRuntimeConfig runtime = resolveRuntime(ownerId);
        if (runtime != null) {
            try {
                return call.run(providerFactory.create(runtime));
            } catch (Exception exception) {
                log.warn("AI provider '{}' failed, falling back to mock: {}",
                        runtime.name(), exception.getMessage());
                AiResult fallback = call.run(mockAiProvider);
                return new AiResult(
                        "你配置的 AI 服务暂时没有连通，我先用本地演示模式完成了这次调整。\n\n"
                                + "可以到「AI 设置」里点一次测试连接，确认 Base URL、模型名和 API Key。\n\n"
                                + fallback.reply(),
                        fallback.spec(),
                        fallback.changed(),
                        "mock-fallback"
                );
            }
        }
        if ("deepseek".equalsIgnoreCase(providerName)) {
            log.warn("app.ai.provider=deepseek but no user AI config and DEEPSEEK_API_KEY is empty; using mock provider");
        }
        return call.run(mockAiProvider);
    }

    private AiRuntimeConfig resolveRuntime(UUID ownerId) {
        try {
            AiRuntimeConfig userConfig = configService.resolveDefault(ownerId);
            if (userConfig != null) {
                return userConfig;
            }
        } catch (Exception exception) {
            log.warn("Unable to load user AI config: {}", exception.getMessage());
        }

        if ("deepseek".equalsIgnoreCase(providerName) && !systemApiKey.isBlank()) {
            return new AiRuntimeConfig(
                    AiProviderType.OPENAI_COMPATIBLE,
                    "DeepSeek",
                    systemBaseUrl,
                    systemApiKey,
                    systemModel
            );
        }
        return null;
    }

    @FunctionalInterface
    private interface AiCall {
        AiResult run(AiProvider provider);
    }
}
