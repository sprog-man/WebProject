package com.xiaoliu.webporduce.image;

public record ImageGenerationResult(
        byte[] bytes,
        String mimeType,
        String sourceUrl
) {
}
