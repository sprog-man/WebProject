package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final MockAiProvider mockAiProvider;
    private final AiProviderFactory providerFactory;
    private final AiConfigService configService;
    private final ObjectMapper objectMapper;
    private final String providerName;
    private final String systemBaseUrl;
    private final String systemApiKey;
    private final String systemModel;

    public AiService(MockAiProvider mockAiProvider,
                     AiProviderFactory providerFactory,
                     AiConfigService configService,
                     ObjectMapper objectMapper,
                     @Value("${app.ai.provider:mock}") String providerName,
                     @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String systemBaseUrl,
                     @Value("${app.ai.deepseek.api-key:}") String systemApiKey,
                     @Value("${app.ai.deepseek.model:deepseek-chat}") String systemModel) {
        this.mockAiProvider = mockAiProvider;
        this.providerFactory = providerFactory;
        this.configService = configService;
        this.objectMapper = objectMapper;
        this.providerName = providerName;
        this.systemBaseUrl = systemBaseUrl;
        this.systemApiKey = systemApiKey == null ? "" : systemApiKey.trim();
        this.systemModel = systemModel;
    }

    public AiResult generate(UUID ownerId, String projectName, JsonNode currentSpec, String message) {
        AiResult result = runWithFallback(ownerId, provider -> provider.generate(projectName, currentSpec, message));
        return applyDesignVariety(result, projectName, message);
    }

    /**
     * 如果模型没有明确指定版式，给首屏/画廊补一个非默认变体。
     * 这样即使模型偷懒，也不会每次都落回同一套 split + grid 骨架。
     */
    private AiResult applyDesignVariety(AiResult result, String projectName, String message) {
        JsonNode specNode = result.spec();
        if (specNode == null || !specNode.isObject()) {
            return result;
        }
        ObjectNode spec = (ObjectNode) specNode.deepCopy();
        JsonNode sections = spec.path("sections");
        if (!sections.isArray() || sections.isEmpty()) {
            return result;
        }

        int seed = Objects.hash(projectName, message) & 0x7fffffff;
        String mode = spec.path("theme").path("mode").asText("ink");
        boolean changed = false;

        for (int index = 0; index < sections.size(); index++) {
            JsonNode sectionNode = sections.get(index);
            if (!sectionNode.isObject()) {
                continue;
            }
            ObjectNode section = (ObjectNode) sectionNode;
            String component = section.path("component").asText("");
            ObjectNode layout = section.path("layout").isObject()
                    ? (ObjectNode) section.get("layout")
                    : objectMapper.createObjectNode();

            if ("hero.split".equals(component)) {
                String variant = layout.path("variant").asText("");
                String align = layout.path("align").asText("");
                if (variant.isBlank() && !"center".equals(align)) {
                    layout.put("variant", chooseHeroVariant(mode, seed));
                    changed = true;
                }
            } else if ("gallery.grid".equals(component)) {
                String variant = layout.path("variant").asText("");
                if (variant.isBlank()) {
                    layout.put("variant", chooseGalleryVariant(sections, index, seed));
                    changed = true;
                }
            }

            if (layout.size() > 0) {
                section.set("layout", layout);
            }
        }

        return changed
                ? new AiResult(result.reply(), spec, true, result.provider())
                : result;
    }

    private String chooseHeroVariant(String mode, int seed) {
        return switch (mode) {
            case "paper", "mono" -> (seed & 1) == 0 ? "center" : "full";
            case "candy" -> (seed & 1) == 0 ? "full" : "stack";
            default -> switch (seed % 3) {
                case 0 -> "full";
                case 1 -> "center";
                default -> "stack";
            };
        };
    }

    private String chooseGalleryVariant(JsonNode sections, int index, int seed) {
        boolean hasFeatureCards = false;
        for (int previous = 0; previous < index; previous++) {
            if ("feature.cards".equals(sections.get(previous).path("component").asText(""))) {
                hasFeatureCards = true;
                break;
            }
        }
        if (hasFeatureCards) {
            return "list";
        }
        int itemCount = sections.get(index).path("props").path("items").size();
        if (itemCount >= 3) {
            return (seed & 1) == 0 ? "masonry" : "list";
        }
        return "grid";
    }

    public AiResult edit(UUID ownerId, JsonNode currentSpec, String message) {
        return runWithFallback(ownerId, provider -> provider.edit(currentSpec, message));
    }

    private AiResult runWithFallback(UUID ownerId, AiCall call) {
        AiRuntimeConfig runtime = resolveRuntime(ownerId);
        if (runtime != null) {
            AiProvider provider;
            try {
                provider = providerFactory.create(runtime);
            } catch (Exception exception) {
                log.warn("Unable to create AI provider '{}': {}", runtime.name(), exception.getMessage());
                return fallbackResult(call, exception);
            }

            Exception lastFailure = null;
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    return call.run(provider);
                } catch (Exception exception) {
                    lastFailure = exception;
                    log.warn("AI provider '{}' failed (attempt {}/2): {}",
                            runtime.name(), attempt, exception.getMessage());
                    if (attempt == 2 || !isRetryable(exception)) {
                        break;
                    }
                }
            }
            return fallbackResult(call, lastFailure);
        }
        if ("deepseek".equalsIgnoreCase(providerName)) {
            log.warn("app.ai.provider=deepseek but no user AI config and DEEPSEEK_API_KEY is empty; using mock provider");
        }
        return call.run(mockAiProvider);
    }

    private AiResult fallbackResult(AiCall call, Exception exception) {
        AiResult fallback = call.run(mockAiProvider);
        return new AiResult(
                "你配置的 AI 服务这次没有成功返回页面。\n\n"
                        + "原因：" + failureReason(exception) + "\n\n"
                        + "我先用本地演示模式完成了本次调整。可以到「AI 设置」点一次测试连接，或者稍后重试。\n\n"
                        + fallback.reply(),
                fallback.spec(),
                fallback.changed(),
                "mock-fallback"
        );
    }

    private String failureReason(Exception exception) {
        if (exception == null) {
            return "未知错误";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        message = message.replaceAll("\\s+", " ").trim();
        return message.length() > 300 ? message.substring(0, 300) + "…" : message;
    }

    private boolean isRetryable(Exception exception) {
        if (exception == null) {
            return false;
        }
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof java.net.http.HttpTimeoutException) {
                return false;
            }
            if (cause instanceof java.io.IOException) {
                return true;
            }
            cause = cause.getCause();
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("模型服务返回错误 429")
                || message.contains("模型服务返回错误 500")
                || message.contains("模型服务返回错误 502")
                || message.contains("模型服务返回错误 503")
                || message.contains("模型服务返回错误 504")
                || message.contains("JSON 无法解析")
                || message.contains("模型返回了空内容");
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
