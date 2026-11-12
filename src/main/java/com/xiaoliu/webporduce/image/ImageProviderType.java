package com.xiaoliu.webporduce.image;

import com.xiaoliu.webporduce.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

public enum ImageProviderType {
    OPENAI_IMAGES(
            "OpenAI Images",
            "https://apihub.agnes-ai.com/v1/images/generations",
            "agnes-image-2.5-flash",
            "兼容 OpenAI Images API 的图像生成服务。可填写完整图像端点，也可填写 /v1 基础地址。"
    );

    private final String label;
    private final String defaultBaseUrl;
    private final String defaultModel;
    private final String description;

    ImageProviderType(String label, String defaultBaseUrl, String defaultModel, String description) {
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public String description() {
        return description;
    }

    public static ImageProviderType parse(String value) {
        if (value == null || value.isBlank()) {
            return OPENAI_IMAGES;
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "IMAGE_PROVIDER_UNSUPPORTED",
                        "不支持的图像服务商协议：" + value
                ));
    }
}
