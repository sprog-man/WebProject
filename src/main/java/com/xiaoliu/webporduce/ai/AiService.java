package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final MockAiProvider mockAiProvider;
    private final DeepSeekAiProvider deepSeekAiProvider;
    private final String providerName;

    public AiService(MockAiProvider mockAiProvider,
                     DeepSeekAiProvider deepSeekAiProvider,
                     @Value("${app.ai.provider:mock}") String providerName) {
        this.mockAiProvider = mockAiProvider;
        this.deepSeekAiProvider = deepSeekAiProvider;
        this.providerName = providerName;
    }

    public AiResult generate(String projectName, JsonNode currentSpec, String message) {
        return runWithFallback(provider -> provider.generate(projectName, currentSpec, message));
    }

    public AiResult edit(JsonNode currentSpec, String message) {
        return runWithFallback(provider -> provider.edit(currentSpec, message));
    }

    private AiResult runWithFallback(AiCall call) {
        boolean useDeepSeek = "deepseek".equalsIgnoreCase(providerName) && deepSeekAiProvider.available();
        if (useDeepSeek) {
            try {
                return call.run(deepSeekAiProvider);
            } catch (Exception exception) {
                log.warn("DeepSeek generation failed, falling back to mock provider: {}", exception.getMessage());
                AiResult fallback = call.run(mockAiProvider);
                return new AiResult(
                        "模型服务暂时没有连通，我先用本地演示模式完成了这次调整。\n\n" + fallback.reply(),
                        fallback.spec(),
                        fallback.changed(),
                        "mock-fallback"
                );
            }
        }
        if ("deepseek".equalsIgnoreCase(providerName)) {
            log.warn("app.ai.provider=deepseek but DEEPSEEK_API_KEY is empty; using mock provider");
        }
        return call.run(mockAiProvider);
    }

    @FunctionalInterface
    private interface AiCall {
        AiResult run(AiProvider provider);
    }
}
