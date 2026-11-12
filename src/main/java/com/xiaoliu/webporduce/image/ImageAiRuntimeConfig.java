package com.xiaoliu.webporduce.image;

/**
 * 图像服务运行时配置。API Key 只在服务端内存中存在，不会返回给浏览器。
 */
public record ImageAiRuntimeConfig(
        ImageProviderType type,
        String name,
        String baseUrl,
        String apiKey,
        String model,
        String size,
        String quality
) {

    public ImageAiRuntimeConfig {
        apiKey = apiKey == null ? "" : apiKey.trim();
        baseUrl = baseUrl == null ? "" : baseUrl.trim();
        model = model == null ? "" : model.trim();
        size = ImageSizes.normalize(size);
        quality = quality == null || quality.isBlank() ? null : quality.trim();
        name = name == null || name.isBlank() ? type.label() : name.trim();
    }

    public boolean hasApiKey() {
        return !apiKey.isBlank();
    }
}
