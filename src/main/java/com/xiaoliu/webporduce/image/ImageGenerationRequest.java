package com.xiaoliu.webporduce.image;

public record ImageGenerationRequest(
        String prompt,
        String size,
        String quality
) {
}
