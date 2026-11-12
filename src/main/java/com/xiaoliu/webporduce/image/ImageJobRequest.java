package com.xiaoliu.webporduce.image;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageJobRequest(
        @NotBlank(message = "缺少图片所在区块")
        String sectionId,

        @NotBlank(message = "缺少图片路径")
        String path,

        @Size(max = 1500, message = "图像提示词不能超过 1500 个字符")
        String prompt
) {
}
