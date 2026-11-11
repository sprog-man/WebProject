package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SaveDraftRequest(
        @NotNull(message = "页面数据不能为空")
        JsonNode spec,

        @Min(value = 0, message = "页面版本不正确")
        long version
) {
}
