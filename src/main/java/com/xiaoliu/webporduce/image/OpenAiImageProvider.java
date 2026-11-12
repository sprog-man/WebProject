package com.xiaoliu.webporduce.image;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * OpenAI Images 协议适配器。兼容返回 b64_json 或图片 URL 的服务。
 */
public class OpenAiImageProvider implements ImageProvider {

    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private final ImageAiRuntimeConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String endpoint;

    public OpenAiImageProvider(ImageAiRuntimeConfig config,
                               ObjectMapper objectMapper,
                               HttpClient httpClient) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.endpoint = endpoint(config.baseUrl());
    }

    @Override
    public ImageGenerationResult generate(ImageGenerationRequest request) {
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isBlank()) {
            throw new ImageProviderException("图像提示词不能为空");
        }
        String size = ImageSizes.normalize(request.size() == null ? config.size() : request.size());
        String quality = request.quality() == null ? config.quality() : request.quality();

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", config.model());
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", size);
            if (quality != null && !quality.isBlank()) {
                body.put("quality", quality.trim());
            }

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (config.hasApiKey()) {
                builder.header("Authorization", "Bearer " + config.apiKey());
            }

            HttpResponse<String> response = httpClient.send(
                    builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ImageProviderException(failureMessage(response.statusCode(), response.body()));
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode item = firstImageNode(root);
            if (item == null || item.isMissingNode() || item.isNull()) {
                throw new ImageProviderException("图像服务没有返回图片数据");
            }

            if (item.isTextual()) {
                String text = item.asText("").trim();
                if (text.startsWith("http://") || text.startsWith("https://")) {
                    return download(text);
                }
                return decodeBase64(text, null);
            }

            String base64 = firstText(item, "b64_json", "b64", "base64", "image_base64");
            if (!base64.isBlank()) {
                return decodeBase64(base64, textOrNull(item, "mime_type", "mimeType"));
            }

            String url = firstText(item, "url", "image_url", "imageUrl");
            if (url.isBlank()) {
                String image = firstText(item, "image");
                if (image.startsWith("data:")) {
                    return decodeBase64(image, null);
                }
                if (image.startsWith("http://") || image.startsWith("https://")) {
                    url = image;
                }
            }
            if (url.isBlank()) {
                throw new ImageProviderException("图像服务返回了无法识别的数据结构");
            }
            return download(url);
        } catch (ImageProviderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ImageProviderException("无法连接图像服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImageProviderException("图像请求被中断", exception);
        } catch (IllegalArgumentException exception) {
            throw new ImageProviderException("图像服务地址不正确：" + exception.getMessage(), exception);
        }
    }

    @Override
    public ImageGenerationResult testConnection() {
        return generate(new ImageGenerationRequest(
                "A simple abstract color field with soft natural light, no text, no logo, no watermark.",
                config.size(),
                config.quality()
        ));
    }

    private JsonNode firstImageNode(JsonNode root) {
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            return data.get(0);
        }
        if (data.isObject()) {
            return data;
        }
        if (data.isTextual()) {
            return data;
        }
        JsonNode images = root.path("images");
        if (images.isArray() && !images.isEmpty()) {
            return images.get(0);
        }
        if (images.isObject()) {
            return images;
        }
        JsonNode output = root.path("output");
        if (output.isArray() && !output.isEmpty()) {
            return output.get(0);
        }
        if (output.isObject()) {
            return output;
        }
        return null;
    }

    private ImageGenerationResult decodeBase64(String raw, String declaredMimeType) {
        String value = raw.trim();
        String mimeFromDataUrl = null;
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma < 0) {
                throw new ImageProviderException("图像服务返回的 Base64 数据格式不正确");
            }
            String header = value.substring(5, comma);
            int semicolon = header.indexOf(';');
            mimeFromDataUrl = semicolon > 0 ? header.substring(0, semicolon) : header;
            value = value.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
        } catch (IllegalArgumentException exception) {
            throw new ImageProviderException("图像服务返回的 Base64 数据无法解析", exception);
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            throw new ImageProviderException("生成图片大小超出限制（最大 8 MB）");
        }
        String mimeType = detectMimeType(bytes);
        if (mimeType == null) {
            mimeType = normalizeMimeType(declaredMimeType != null ? declaredMimeType : mimeFromDataUrl);
        }
        if (mimeType == null) {
            throw new ImageProviderException("图像服务返回了不支持的图片格式");
        }
        return new ImageGenerationResult(bytes, mimeType, null);
    }

    private ImageGenerationResult download(String rawUrl) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException exception) {
            throw new ImageProviderException("图像服务返回的图片地址不正确", exception);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new ImageProviderException("图像服务返回了不支持的图片地址");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120))
                .header("Accept", "image/*")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            try (InputStream ignored = response.body()) {
                throw new ImageProviderException("下载生成图片失败（HTTP " + response.statusCode() + "）");
            }
        }

        byte[] bytes;
        try (InputStream stream = response.body()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_BYTES) {
                    throw new ImageProviderException("生成图片大小超出限制（最大 8 MB）");
                }
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }
        if (bytes.length == 0) {
            throw new ImageProviderException("图像服务返回了空图片");
        }

        String mimeType = detectMimeType(bytes);
        if (mimeType == null) {
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            mimeType = normalizeMimeType(contentType);
        }
        if (mimeType == null) {
            throw new ImageProviderException("图像服务返回了不支持的图片格式");
        }
        return new ImageGenerationResult(bytes, mimeType, rawUrl);
    }

    private String endpoint(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/images/generations")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/images/generations";
        }
        if (normalized.contains("/v1/")) {
            return normalized + "/images/generations";
        }
        return normalized + "/v1/images/generations";
    }

    private String failureMessage(int status, String body) {
        String detail = "";
        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(body);
                detail = firstText(root.path("error"), "message");
                if (detail.isBlank()) {
                    detail = firstText(root, "message", "detail");
                }
            } catch (Exception ignored) {
                detail = body.replaceAll("\\s+", " ").trim();
            }
        }
        if (detail.isBlank()) {
            return "图像服务返回错误 " + status;
        }
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return "图像服务返回错误 " + status + "：" + detail;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText("").isBlank()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private String textOrNull(JsonNode node, String... fields) {
        String value = firstText(node, fields);
        return value.isBlank() ? null : value;
    }

    private String normalizeMimeType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.toLowerCase().trim();
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        return switch (value) {
            case "image/png", "image/jpeg", "image/jpg", "image/webp", "image/gif" ->
                    value.equals("image/jpg") ? "image/jpeg" : value;
            default -> null;
        };
    }

    private String detectMimeType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
