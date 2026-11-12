package com.xiaoliu.webporduce.image;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateImageJobsRequest(
        @NotEmpty(message = "请选择要生成的图片")
        @Size(max = 5, message = "一次最多生成 5 张图片")
        List<@Valid ImageJobRequest> items
) {
}
