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

public class AnthropicProvider extends AbstractJsonAiProvider {

    private final HttpClient httpClient;
    private final String endpoint;

    public AnthropicProvider(AiRuntimeConfig config,
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
    public boolean available() {
        return super.available() && config.hasApiKey();
    }

    @Override
    protected JsonNode callModel(String userPrompt, double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.model());
            body.put("max_tokens", 4096);
            body.put("temperature", temperature);
            body.put("system", systemPrompt);

            ArrayNode messages = body.putArray("messages");
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-api-key", config.apiKey())
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiProviderException(requestFailureMessage(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            StringBuilder text = new StringBuilder();
            for (JsonNode block : root.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText(""));
                }
            }
            return parseJsonObject(text.toString());
        } catch (IOException exception) {
            throw new AiProviderException("无法连接 Anthropic 服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Anthropic 请求被中断", exception);
        }
    }

    private String endpoint(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith("/messages")) {
            return normalized;
        }
        return normalized + "/messages";
    }
}
