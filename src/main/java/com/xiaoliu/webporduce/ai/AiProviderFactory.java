package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.ObjectMapper;
import com.xiaoliu.webporduce.project.PageSpecValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class AiProviderFactory {

    private final ObjectMapper objectMapper;
    private final PageSpecValidator specValidator;
    private final SpecPatchApplier patchApplier;
    private final String systemPrompt;
    private final HttpClient httpClient;

    public AiProviderFactory(ObjectMapper objectMapper,
                             PageSpecValidator specValidator,
                             SpecPatchApplier patchApplier) {
        this.objectMapper = objectMapper;
        this.specValidator = specValidator;
        this.patchApplier = patchApplier;
        this.systemPrompt = loadSystemPrompt();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public AiProvider create(AiRuntimeConfig config) {
        if (config == null) {
            throw new AiProviderException("AI 配置为空");
        }
        AiProvider provider = switch (config.type()) {
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(
                    config, objectMapper, specValidator, patchApplier, systemPrompt, httpClient);
            case ANTHROPIC -> new AnthropicProvider(
                    config, objectMapper, specValidator, patchApplier, systemPrompt, httpClient);
            case GEMINI -> new GeminiProvider(
                    config, objectMapper, specValidator, patchApplier, systemPrompt, httpClient);
        };
        if (!provider.available()) {
            throw new AiProviderException("AI 配置不完整，请检查 Base URL、模型名和 API Key");
        }
        return provider;
    }

    private String loadSystemPrompt() {
        try {
            return new ClassPathResource("ai/system-prompt.txt")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("无法加载 AI 系统提示词", exception);
        }
    }
}
