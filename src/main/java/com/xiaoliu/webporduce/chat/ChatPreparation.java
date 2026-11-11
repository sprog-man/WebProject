package com.xiaoliu.webporduce.chat;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ChatPreparation(
        UUID projectId,
        String projectName,
        JsonNode spec,
        long version,
        MessageResponse userMessage,
        String content,
        Instant createdAt
) {
}
