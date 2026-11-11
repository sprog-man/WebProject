package com.xiaoliu.webporduce.ai;

public record AiConfigTestResponse(
        boolean ok,
        String message,
        long latencyMs,
        String model
) {
}
