package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.project.PageSpecValidator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenAiCompatibleProvider extends AbstractJsonAiProvider {

    private final HttpClient httpClient;
    private final String endpoint;

    public OpenAiCompatibleProvider(AiRuntimeConfig config,
                                    ObjectMapper objectMapper,
                                    PageSpecValidator specValidator,
                                    SpecPatchApplier patchApplier,
                                    String systemPrompt,
                                    HttpClient httpClient) {
        super(config, objectMapper, specValidator, patchApplier, systemPrompt);
        this.httpClient = httpClient;
        this.endpoint = endpoint(config.baseUrl());
    }

    @Override
    protected JsonNode callModel(String userPrompt, double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.model());
            body.put("temperature", temperature);
            body.put("max_tokens", 4096);
            body.put("stream", false);

            ArrayNode messages = body.putArray("messages");
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (config.hasApiKey()) {
                requestBuilder.header("Authorization", "Bearer " + config.apiKey());
            }

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(requestFailureMessage(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return parseJsonObject(content);
        } catch (IOException exception) {
            throw new AiProviderException("无法连接模型服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("模型请求被中断", exception);
        }
    }

    private String endpoint(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        return normalized + "/chat/completions";
    }
}
