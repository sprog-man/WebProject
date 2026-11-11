package com.xiaoliu.webporduce.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiConfigRequest(
        @NotBlank(message = "请填写配置名称")
        @Size(max = 80, message = "配置名称不能超过 80 个字符")
        String name,

        @NotBlank(message = "请选择 AI 服务商")
        String providerType,

        @NotBlank(message = "请填写 Base URL")
        @Size(max = 500, message = "Base URL 不能超过 500 个字符")
        String baseUrl,

        @NotBlank(message = "请填写模型名")
        @Size(max = 160, message = "模型名不能超过 160 个字符")
        String model,

        @Size(max = 4096, message = "API Key 过长")
        String apiKey,

        boolean defaultConfig
) {
}
