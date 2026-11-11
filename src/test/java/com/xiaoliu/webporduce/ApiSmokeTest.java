package com.xiaoliu.webporduce;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    CookieManager cookies;
    HttpClient client;
    String baseUrl;
    String csrfHeader;
    String csrfToken;

    @BeforeEach
    void setUp() throws Exception {
        cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        baseUrl = "http://localhost:" + port;

        HttpResponse<String> csrf = sendGet("/api/auth/csrf");
        assertThat(csrf.statusCode()).isEqualTo(200);
        JsonNode csrfBody = objectMapper.readTree(csrf.body());
        csrfHeader = csrfBody.path("headerName").asText();
        csrfToken = csrfBody.path("token").asText();
        assertThat(csrfToken).isNotBlank();
    }

    @Test
    void registerCreateEditSaveAndExportAssets() throws Exception {
        HttpResponse<String> home = sendGet("/");
        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("Aster");

        HttpResponse<String> preset = sendGet("/presets/visual-01.svg");
        assertThat(preset.statusCode()).isEqualTo(200);
        assertThat(preset.body()).contains("<svg");

        HttpResponse<String> register = sendJson("POST", "/api/auth/register", """
                {
                  "email": "smoke-%s@example.com",
                  "password": "password123",
                  "displayName": "冒烟测试"
                }
                """.formatted(UUID.randomUUID().toString().replace("-", "")));
        assertThat(register.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(register.body()).path("displayName").asText()).isEqualTo("冒烟测试");

        HttpResponse<String> anonymous = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/projects")).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(anonymous.statusCode()).isEqualTo(401);

        HttpResponse<String> create = sendJson("POST", "/api/projects", """
                {"name": "自动化测试页面"}
                """);
        assertThat(create.statusCode()).isEqualTo(201);
        JsonNode detail = objectMapper.readTree(create.body());
        String projectId = detail.path("id").asText();
        long version = detail.path("version").asLong();
        assertThat(projectId).isNotBlank();
        assertThat(version).isEqualTo(0);
        assertThat(detail.path("spec").path("sections").isArray()).isTrue();
        assertThat(detail.path("spec").path("sections").size()).isZero();

        HttpResponse<String> chat = sendJson("POST", "/api/projects/" + projectId + "/messages", """
                {"content": "把标题更大一点"}
                """);
        assertThat(chat.statusCode()).isEqualTo(200);
        JsonNode chatBody = objectMapper.readTree(chat.body());
        assertThat(chatBody.path("changed").asBoolean()).isTrue();
        assertThat(chatBody.path("version").asLong()).isGreaterThan(version);
        assertThat(chatBody.path("spec").path("sections").size()).isGreaterThan(0);
        assertThat(chatBody.path("assistantMessage").path("content").asText()).isNotBlank();

        JsonNode spec = chatBody.path("spec");
        ((ObjectNode) spec.path("meta")).put("title", "自动化测试页面 · 已保存");
        ObjectNode draft = objectMapper.createObjectNode();
        draft.set("spec", spec);
        draft.put("version", chatBody.path("version").asLong());
        HttpResponse<String> save = sendJson("PUT", "/api/projects/" + projectId + "/draft",
                objectMapper.writeValueAsString(draft));
        assertThat(save.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(save.body()).path("version").asLong())
                .isEqualTo(chatBody.path("version").asLong() + 1);

        HttpResponse<String> reload = sendGet("/api/projects/" + projectId);
        assertThat(reload.statusCode()).isEqualTo(200);
        JsonNode reloaded = objectMapper.readTree(reload.body());
        assertThat(reloaded.path("spec").path("meta").path("title").asText())
                .isEqualTo("自动化测试页面 · 已保存");
        assertThat(reloaded.path("messages").size()).isGreaterThanOrEqualTo(2);

        ObjectNode staleDraft = objectMapper.createObjectNode();
        staleDraft.set("spec", spec);
        staleDraft.put("version", version);
        HttpResponse<String> conflict = sendJson("PUT", "/api/projects/" + projectId + "/draft",
                objectMapper.writeValueAsString(staleDraft));
        assertThat(conflict.statusCode()).isEqualTo(409);
    }

    @Test
    void aiConfigCrudDefaultAndTestEndpoint() throws Exception {
        HttpResponse<String> register = sendJson("POST", "/api/auth/register", """
                {
                  "email": "ai-smoke-%s@example.com",
                  "password": "password123",
                  "displayName": "AI 配置测试"
                }
                """.formatted(UUID.randomUUID().toString().replace("-", "")));
        assertThat(register.statusCode()).isEqualTo(201);

        HttpResponse<String> providers = sendGet("/api/ai/providers");
        assertThat(providers.statusCode()).isEqualTo(200);
        JsonNode presetList = objectMapper.readTree(providers.body());
        assertThat(presetList.size()).isEqualTo(3);
        assertThat(presetList.path(0).path("type").asText()).isEqualTo("OPENAI_COMPATIBLE");

        HttpResponse<String> created = sendJson("POST", "/api/ai/configs", """
                {
                  "name": "错误地址配置",
                  "providerType": "OPENAI_COMPATIBLE",
                  "baseUrl": "http://127.0.0.1:9/v1",
                  "model": "test-model",
                  "apiKey": "sk-smoke-12345678",
                  "defaultConfig": true
                }
                """);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode first = objectMapper.readTree(created.body());
        String firstId = first.path("id").asText();
        assertThat(first.path("defaultConfig").asBoolean()).isTrue();
        assertThat(first.path("keyHint").asText()).isEqualTo("5678");
        assertThat(first.path("hasApiKey").asBoolean()).isTrue();
        assertThat(first.has("apiKeyEncrypted")).isFalse();

        HttpResponse<String> updated = sendJson("PUT", "/api/ai/configs/" + firstId, """
                {
                  "name": "错误地址配置已更新",
                  "providerType": "OPENAI_COMPATIBLE",
                  "baseUrl": "http://127.0.0.1:9/v1",
                  "model": "test-model-2",
                  "apiKey": "",
                  "defaultConfig": true
                }
                """);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode updatedBody = objectMapper.readTree(updated.body());
        assertThat(updatedBody.path("name").asText()).isEqualTo("错误地址配置已更新");
        assertThat(updatedBody.path("hasApiKey").asBoolean()).isTrue();
        assertThat(updatedBody.path("keyHint").asText()).isEqualTo("5678");

        HttpResponse<String> tested = sendJson("POST", "/api/ai/configs/" + firstId + "/test", "");
        assertThat(tested.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(tested.body()).path("ok").asBoolean()).isFalse();

        HttpResponse<String> second = sendJson("POST", "/api/ai/configs", """
                {
                  "name": "备用配置",
                  "providerType": "GEMINI",
                  "baseUrl": "http://127.0.0.1:9/v1beta",
                  "model": "gemini-test",
                  "apiKey": "gemini-smoke-key",
                  "defaultConfig": false
                }
                """);
        assertThat(second.statusCode()).isEqualTo(201);
        String secondId = objectMapper.readTree(second.body()).path("id").asText();

        HttpResponse<String> makeDefault = sendJson("POST", "/api/ai/configs/" + secondId + "/default", "");
        assertThat(makeDefault.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(makeDefault.body()).path("defaultConfig").asBoolean()).isTrue();

        HttpResponse<String> list = sendGet("/api/ai/configs");
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode configs = objectMapper.readTree(list.body());
        assertThat(configs.size()).isEqualTo(2);
        assertThat(configs.path(0).path("defaultConfig").asBoolean()).isFalse();
        assertThat(configs.path(1).path("defaultConfig").asBoolean()).isTrue();

        HttpResponse<String> deleteDefault = sendJson("DELETE", "/api/ai/configs/" + secondId, "");
        assertThat(deleteDefault.statusCode()).isEqualTo(204);

        HttpResponse<String> afterDelete = sendGet("/api/ai/configs");
        JsonNode remaining = objectMapper.readTree(afterDelete.body());
        assertThat(remaining.size()).isEqualTo(1);
        assertThat(remaining.path(0).path("defaultConfig").asBoolean()).isTrue();

        HttpResponse<String> deleteLast = sendJson("DELETE", "/api/ai/configs/" + firstId, "");
        assertThat(deleteLast.statusCode()).isEqualTo(204);
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Accept", "application/json, text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header(csrfHeader, csrfToken)
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
