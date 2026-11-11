package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.xiaoliu.webporduce.project.PageSpecValidator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class GeminiProvider extends AbstractJsonAiProvider {

    private final HttpClient httpClient;
    private final String endpoint;

    public GeminiProvider(AiRuntimeConfig config,
                          ObjectMapper objectMapper,
                          PageSpecValidator specValidator,
                          SpecPatchApplier patchApplier,
                          String systemPrompt,
                          HttpClient httpClient) {
        super(config, objectMapper, specValidator, patchApplier, systemPrompt);
        this.httpClient = httpClient;
        this.endpoint = endpoint(config.baseUrl(), config.model());
    }

    @Override
    public boolean available() {
        return super.available() && config.hasApiKey();
    }

    @Override
    protected JsonNode callModel(String userPrompt, double temperature) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode systemInstruction = body.putObject("system_instruction");
            ArrayNode systemParts = systemInstruction.putArray("parts");
            systemParts.addObject().put("text", systemPrompt);

            ArrayNode contents = body.putArray("contents");
            ObjectNode user = contents.addObject();
            user.put("role", "user");
            ArrayNode userParts = user.putArray("parts");
            userParts.addObject().put("text", userPrompt);

            ObjectNode generationConfig = body.putObject("generationConfig");
            generationConfig.put("temperature", temperature);
            generationConfig.put("maxOutputTokens", 4096);
            generationConfig.put("responseMimeType", "application/json");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("x-goog-api-key", config.apiKey())
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
            for (JsonNode part : root.path("candidates").path(0).path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
            return parseJsonObject(text.toString());
        } catch (IOException exception) {
            throw new AiProviderException("无法连接 Gemini 服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Gemini 请求被中断", exception);
        }
    }

    private String endpoint(String baseUrl, String model) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (normalized.endsWith(":generateContent")) {
            return normalized;
        }
        String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20");
        return normalized + "/models/" + encodedModel + ":generateContent";
    }
}
