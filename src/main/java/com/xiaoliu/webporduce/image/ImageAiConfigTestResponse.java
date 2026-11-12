package com.xiaoliu.webporduce.image;

public record ImageAiConfigTestResponse(
        boolean ok,
        String message,
        long latencyMs,
        String model,
        String size,
        String previewUrl,
        String mimeType,
        Integer width,
        Integer height
) {
}
