package com.xiaoliu.webporduce.project;

import jakarta.validation.constraints.Size;

public record RenameProjectRequest(
        @jakarta.validation.constraints.NotBlank(message = "请输入页面名称")
        @Size(max = 120, message = "页面名称不能超过 120 个字符")
        String name
) {
}
