package com.xiaoliu.webporduce.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "请输入密码")
        String password
) {
}
