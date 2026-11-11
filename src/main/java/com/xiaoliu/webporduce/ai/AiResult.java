package com.xiaoliu.webporduce.ai;

import tools.jackson.databind.JsonNode;

public record AiResult(
        String reply,
        JsonNode spec,
        boolean changed,
        String provider
) {
}
