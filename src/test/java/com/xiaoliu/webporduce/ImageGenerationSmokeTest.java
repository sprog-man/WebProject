package com.xiaoliu.webporduce;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 用本地桩图像服务验证图像 AI 的完整链路：
 * 配置加密 → 测试连接返回真实预览图 → 确认后只生成提交的任务 →
 * 异步任务写回 PageSpec → 失败保留原图 → 刷新后仍可读取生成图。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ImageGenerationSmokeTest {

    private static final String API_KEY = "sk-image-secret-9876";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    CookieManager cookies;
    HttpClient client;
    String baseUrl;
    String csrfHeader;
    String csrfToken;
    HttpServer imageServer;
    String imageBaseUrl;
    String pngBase64;

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

        pngBase64 = tinyPngBase64();
        imageServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        imageServer.createContext("/v1/images/generations", this::handleImageRequest);
        imageServer.start();
        imageBaseUrl = "http://127.0.0.1:" + imageServer.getAddress().getPort() + "/v1/images/generations";
    }

    @AfterEach
    void tearDown() {
        if (imageServer != null) {
            imageServer.stop(0);
        }
    }

    @Test
    void imageConfigTestGenerateApplyAndFailureKeepsCurrentImage() throws Exception {
        register("image-%s@example.com");

        HttpResponse<String> providers = sendGet("/api/image-ai/providers");
        assertThat(providers.statusCode()).isEqualTo(200);
        JsonNode presets = objectMapper.readTree(providers.body());
        assertThat(presets.size()).isEqualTo(1);
        assertThat(presets.path(0).path("type").asText()).isEqualTo("OPENAI_IMAGES");

        HttpResponse<String> createdProject = sendJson("POST", "/api/projects", """
                {"name": "图像生成测试页"}
                """);
        assertThat(createdProject.statusCode()).isEqualTo(201);
        String projectId = objectMapper.readTree(createdProject.body()).path("id").asText();

        HttpResponse<String> chat = sendJson("POST", "/api/projects/" + projectId + "/messages", """
                {"content": "为一家独立咖啡品牌做官网"}
                """);
        assertThat(chat.statusCode()).isEqualTo(200);
        JsonNode chatBody = objectMapper.readTree(chat.body());
        JsonNode spec = chatBody.path("spec");
        ImageTarget target = firstImage(spec);
        assertThat(target).isNotNull();
        assertThat(target.image.path("prompt").asText(""))
                .as("文本 AI 生成的页面应该自带图像提示词")
                .isNotBlank();
        assertAllImagesHavePrompts(spec, 1);

        HttpResponse<String> designProject = sendJson("POST", "/api/projects", """
                {"name": "多图提示词测试页"}
                """);
        assertThat(designProject.statusCode()).isEqualTo(201);
        String designProjectId = objectMapper.readTree(designProject.body()).path("id").asText();
        HttpResponse<String> designChat = sendJson("POST", "/api/projects/" + designProjectId + "/messages", """
                {"content": "为一家独立设计工作室做官网"}
                """);
        assertThat(designChat.statusCode()).isEqualTo(200);
        assertAllImagesHavePrompts(objectMapper.readTree(designChat.body()).path("spec"), 2);

        HttpResponse<String> createdConfig = sendJson("POST", "/api/image-ai/configs", """
                {
                  "name": "本地桩图像模型",
                  "providerType": "OPENAI_IMAGES",
                  "baseUrl": "%s",
                  "model": "test-image-model",
                  "apiKey": "%s",
                  "defaultSize": "1024x1024",
                  "defaultConfig": true
                }
                """.formatted(imageBaseUrl, API_KEY));
        assertThat(createdConfig.statusCode()).isEqualTo(201);
        JsonNode configBody = objectMapper.readTree(createdConfig.body());
        String configId = configBody.path("id").asText();
        assertThat(configBody.path("hasApiKey").asBoolean()).isTrue();
        assertThat(configBody.path("keyHint").asText()).isEqualTo("9876");
        assertThat(createdConfig.body()).doesNotContain(API_KEY);
        assertThat(configBody.has("apiKey")).isFalse();
        assertThat(configBody.has("apiKeyEncrypted")).isFalse();

        HttpResponse<String> configList = sendGet("/api/image-ai/configs");
        assertThat(configList.statusCode()).isEqualTo(200);
        assertThat(configList.body()).doesNotContain(API_KEY);
        assertThat(configList.body()).doesNotContain("apiKeyEncrypted");
        assertThat(configList.body()).doesNotContain("apiKey\"");

        HttpResponse<String> updatedConfig = sendJson("PUT", "/api/image-ai/configs/" + configId, """
                {
                  "name": "本地桩图像模型（已编辑）",
                  "providerType": "OPENAI_IMAGES",
                  "baseUrl": "%s",
                  "model": "test-image-model-v2",
                  "apiKey": "",
                  "defaultSize": "1024x1024",
                  "defaultConfig": true
                }
                """.formatted(imageBaseUrl));
        assertThat(updatedConfig.statusCode()).as(updatedConfig.body()).isEqualTo(200);
        JsonNode updatedBody = objectMapper.readTree(updatedConfig.body());
        assertThat(updatedBody.path("name").asText()).isEqualTo("本地桩图像模型（已编辑）");
        assertThat(updatedBody.path("model").asText()).isEqualTo("test-image-model-v2");
        assertThat(updatedBody.path("hasApiKey").asBoolean()).isTrue();
        assertThat(updatedBody.path("keyHint").asText()).isEqualTo("9876");
        assertThat(updatedConfig.body()).doesNotContain(API_KEY);

        HttpResponse<String> tested = sendJson("POST", "/api/image-ai/configs/" + configId + "/test", "");
        assertThat(tested.statusCode()).isEqualTo(200);
        JsonNode testBody = objectMapper.readTree(tested.body());
        assertThat(testBody.path("ok").asBoolean()).as(tested.body()).isTrue();
        assertThat(testBody.path("previewUrl").asText()).startsWith("/api/assets/");
        assertThat(testBody.path("mimeType").asText()).isEqualTo("image/png");
        assertThat(tested.body()).doesNotContain(API_KEY);

        HttpResponse<byte[]> preview = sendBytes("/api/assets/"
                + testBody.path("previewUrl").asText().split("/")[3] + "/content");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body().length).isGreaterThan(0);

        HttpResponse<String> submitted = sendJson("POST", "/api/projects/" + projectId + "/image-jobs", """
                {
                  "items": [
                    {
                      "sectionId": "%s",
                      "path": "%s",
                      "prompt": "%s"
                    }
                  ]
                }
                """.formatted(target.sectionId, target.path, escape(target.image.path("prompt").asText())));
        assertThat(submitted.statusCode()).as(submitted.body()).isEqualTo(200);
        JsonNode submittedBody = objectMapper.readTree(submitted.body());
        assertThat(submittedBody.path("jobs").size()).isEqualTo(1);
        String jobId = submittedBody.path("jobs").path(0).path("id").asText();
        String initialStatus = submittedBody.path("jobs").path(0).path("status").asText();
        assertThat(initialStatus).isIn("PENDING", "RUNNING", "SUCCEEDED");

        JsonNode completed = waitForJob(projectId, jobId, "SUCCEEDED");
        assertThat(completed.path("assetUrl").asText()).startsWith("/api/assets/");
        assertThat(completed.path("errorMessage").isNull()).isTrue();

        HttpResponse<String> reloaded = sendGet("/api/projects/" + projectId);
        JsonNode reloadedSpec = objectMapper.readTree(reloaded.body()).path("spec");
        JsonNode appliedImage = imageAt(reloadedSpec, target.sectionId, target.path);
        String generatedSrc = appliedImage.path("src").asText();
        String generatedAssetId = appliedImage.path("assetId").asText();
        assertThat(generatedSrc).startsWith("/api/assets/");
        assertThat(generatedAssetId).isNotBlank();

        HttpResponse<byte[]> generatedBytes = sendBytes(generatedSrc);
        assertThat(generatedBytes.statusCode()).isEqualTo(200);
        assertThat(generatedBytes.body().length).isGreaterThan(0);

        long version = objectMapper.readTree(reloaded.body()).path("version").asLong();
        ObjectNode nextSpec = (ObjectNode) reloadedSpec;
        ((ObjectNode) appliedImage).put("prompt", "A new prompt that will fail to generate.");
        ObjectNode draft = objectMapper.createObjectNode();
        draft.set("spec", nextSpec);
        draft.put("version", version);
        HttpResponse<String> saved = sendJson("PUT", "/api/projects/" + projectId + "/draft",
                objectMapper.writeValueAsString(draft));
        assertThat(saved.statusCode()).isEqualTo(200);

        HttpResponse<String> badConfig = sendJson("POST", "/api/image-ai/configs", """
                {
                  "name": "不可用图像模型",
                  "providerType": "OPENAI_IMAGES",
                  "baseUrl": "http://127.0.0.1:9/v1/images/generations",
                  "model": "bad-model",
                  "apiKey": "sk-bad-key-0001",
                  "defaultSize": "512x512",
                  "defaultConfig": true
                }
                """);
        assertThat(badConfig.statusCode()).isEqualTo(201);

        HttpResponse<String> failing = sendJson("POST", "/api/projects/" + projectId + "/image-jobs", """
                {
                  "items": [
                    {
                      "sectionId": "%s",
                      "path": "%s",
                      "prompt": "A new prompt that will fail to generate."
                    }
                  ]
                }
                """.formatted(target.sectionId, target.path));
        assertThat(failing.statusCode()).as(failing.body()).isEqualTo(200);
        String failingJobId = objectMapper.readTree(failing.body()).path("jobs").path(0).path("id").asText();

        JsonNode failed = waitForJob(projectId, failingJobId, "FAILED");
        assertThat(failed.path("errorMessage").asText()).isNotBlank();

        JsonNode afterFailure = objectMapper.readTree(sendGet("/api/projects/" + projectId).body()).path("spec");
        JsonNode keptImage = imageAt(afterFailure, target.sectionId, target.path);
        assertThat(keptImage.path("src").asText())
                .as("生成失败时应保留上一张图片")
                .isEqualTo(generatedSrc);
        assertThat(keptImage.path("assetId").asText()).isEqualTo(generatedAssetId);

        String badConfigId = objectMapper.readTree(badConfig.body()).path("id").asText();
        HttpResponse<String> deleted = sendJson("DELETE", "/api/image-ai/configs/" + badConfigId, "");
        assertThat(deleted.statusCode()).isEqualTo(204);
        JsonNode remainingConfigs = objectMapper.readTree(sendGet("/api/image-ai/configs").body());
        assertThat(remainingConfigs.size()).isEqualTo(1);
        assertThat(remainingConfigs.path(0).path("defaultConfig").asBoolean()).isTrue();
        assertThat(remainingConfigs.path(0).path("id").asText()).isEqualTo(configId);
    }

    private void register(String emailTemplate) throws Exception {
        HttpResponse<String> register = sendJson("POST", "/api/auth/register", """
                {
                  "email": "%s",
                  "password": "password123",
                  "displayName": "图像生成测试"
                }
                """.formatted(emailTemplate.formatted(
                UUID.randomUUID().toString().replace("-", ""))));
        assertThat(register.statusCode()).isEqualTo(201);
    }

    private void handleImageRequest(HttpExchange exchange) throws IOException {
        try {
            exchange.getRequestBody().readAllBytes();
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (!("Bearer " + API_KEY).equals(authorization)) {
                respond(exchange, 401, "{\"error\":{\"message\":\"invalid api key\"}}");
                return;
            }
            respond(exchange, 200, "{\"data\":[{\"b64_json\":\"" + pngBase64 + "\"}]}");
        } finally {
            exchange.close();
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private JsonNode waitForJob(String projectId, String jobId, String expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            JsonNode body = objectMapper.readTree(
                    sendGet("/api/projects/" + projectId + "/image-jobs").body()
            );
            for (JsonNode job : body.path("jobs")) {
                if (!jobId.equals(job.path("id").asText())) {
                    continue;
                }
                last = job;
                String status = job.path("status").asText();
                if (expected.equals(status)) {
                    return job;
                }
                if ("FAILED".equals(status) || "SUCCEEDED".equals(status) || "SUPERSEDED".equals(status)) {
                    fail("任务状态为 " + status + "，期望 " + expected + "：" + job);
                }
            }
            Thread.sleep(150);
        }
        fail("等待图片任务超时，最后状态：" + last);
        return last;
    }

    private ImageTarget firstImage(JsonNode spec) {
        for (JsonNode section : spec.path("sections")) {
            JsonNode image = section.path("props").path("image");
            if (image.isObject()) {
                return new ImageTarget(section.path("id").asText(), "props.image", image);
            }
        }
        return null;
    }

    private void assertAllImagesHavePrompts(JsonNode spec, int minCount) {
        int count = 0;
        for (JsonNode section : spec.path("sections")) {
            JsonNode heroImage = section.path("props").path("image");
            if (heroImage.isObject()) {
                assertThat(heroImage.path("prompt").asText(""))
                        .as("区块 %s 的图片缺少提示词", section.path("id").asText())
                        .isNotBlank();
                count++;
            }
            for (JsonNode item : section.path("props").path("items")) {
                JsonNode itemImage = item.path("image");
                if (itemImage.isObject()) {
                    assertThat(itemImage.path("prompt").asText(""))
                            .as("区块 %s 的画廊图片缺少提示词", section.path("id").asText())
                            .isNotBlank();
                    count++;
                }
            }
        }
        assertThat(count).isGreaterThanOrEqualTo(minCount);
    }

    private JsonNode imageAt(JsonNode spec, String sectionId, String path) {
        for (JsonNode section : spec.path("sections")) {
            if (!sectionId.equals(section.path("id").asText())) {
                continue;
            }
            JsonNode current = section;
            for (String segment : path.split("\\.")) {
                current = current.path(segment);
            }
            return current;
        }
        return objectMapper.createObjectNode();
    }

    private String tinyPngBase64() throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Accept", "application/json, text/html")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header(csrfHeader, csrfToken)
                .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private record ImageTarget(String sectionId, String path, JsonNode image) {
    }
}
