package com.xiaoliu.webporduce.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "请输入密码")
        @Size(min = 8, max = 72, message = "密码长度需要在 8 到 72 位之间")
        String password,

        @NotBlank(message = "请输入昵称")
        @Size(max = 40, message = "昵称不能超过 40 个字符")
        String displayName
) {
}
