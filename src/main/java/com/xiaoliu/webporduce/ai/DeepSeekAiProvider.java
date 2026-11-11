package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.project.PageSpecValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
public class DeepSeekAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiProvider.class);

    private final ObjectMapper objectMapper;
    private final PageSpecValidator specValidator;
    private final SpecPatchApplier patchApplier;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;
    private final HttpClient httpClient;

    public DeepSeekAiProvider(ObjectMapper objectMapper,
                              PageSpecValidator specValidator,
                              SpecPatchApplier patchApplier,
                              @Value("${app.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
                              @Value("${app.ai.deepseek.api-key:}") String apiKey,
                              @Value("${app.ai.deepseek.model:deepseek-chat}") String model) {
        this.objectMapper = objectMapper;
        this.specValidator = specValidator;
        this.patchApplier = patchApplier;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.systemPrompt = loadSystemPrompt();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public String name() {
        return "deepseek";
    }

    @Override
    public boolean available() {
        return !apiKey.isBlank();
    }

    @Override
    public AiResult generate(String projectName, JsonNode currentSpec, String message) {
        String prompt = """
                【模式】生成
                【项目名称】%s
                【用户需求】%s

                请返回 {"reply": "...", "pageSpec": {...}}。
                """.formatted(projectName, message);
        JsonNode response = callModel(prompt, 0.7);
        JsonNode spec = response.get("pageSpec");
        if (spec == null || !spec.isObject()) {
            throw new AiProviderException("模型没有返回 pageSpec");
        }
        specValidator.validate(spec);
        ObjectNode meta = (ObjectNode) spec.path("meta");
        if (meta.path("title").asText("").isBlank()) {
            meta.put("title", projectName);
        }
        if (!spec.has("fieldMeta")) {
            ((ObjectNode) spec).putObject("fieldMeta");
        }
        return new AiResult(
                response.path("reply").asText("页面已经生成，可以直接点选修改。"),
                spec,
                true,
                name()
        );
    }

    @Override
    public AiResult edit(JsonNode currentSpec, String message) {
        String specJson;
        try {
            specJson = objectMapper.writeValueAsString(currentSpec);
        } catch (Exception exception) {
            throw new AiProviderException("无法读取当前页面数据", exception);
        }
        String prompt = """
                【模式】局部编辑
                【当前 PageSpec】
                %s

                【用户修改要求】
                %s

                请只返回 {"reply": "...", "patches": [...]}，不要返回完整页面。
                """.formatted(specJson, message);
        JsonNode response = callModel(prompt, 0.35);
        JsonNode patches = response.get("patches");
        JsonNode nextSpec = patchApplier.apply(currentSpec, patches);
        specValidator.validate(nextSpec);
        return new AiResult(
                response.path("reply").asText("已经按你的要求更新了画布。"),
                nextSpec,
                !nextSpec.equals(currentSpec),
                name()
        );
    }

    private JsonNode callModel(String userPrompt, double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("max_tokens", 4096);
            body.put("stream", false);
            ObjectNode format = body.putObject("response_format");
            format.put("type", "json_object");

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                log.warn("DeepSeek request failed: {} {}", httpResponse.statusCode(), httpResponse.body());
                throw new AiProviderException("模型服务返回错误：" + httpResponse.statusCode());
            }

            JsonNode root = objectMapper.readTree(httpResponse.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                throw new AiProviderException("模型返回了空内容");
            }
            return parseJsonObject(content);
        } catch (IOException exception) {
            throw new AiProviderException("无法连接模型服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("模型请求被中断", exception);
        }
    }

    private JsonNode parseJsonObject(String content) {
        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception firstFailure) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(cleaned.substring(start, end + 1));
                } catch (Exception ignored) {
                    // fall through
                }
            }
            throw new AiProviderException("模型返回的 JSON 无法解析", firstFailure);
        }
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
