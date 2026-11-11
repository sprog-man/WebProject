package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;
import com.xiaoliu.webporduce.chat.MessageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectDetail(
        UUID id,
        String name,
        long version,
        JsonNode spec,
        List<MessageResponse> messages,
        Long latestRevision,
        Instant createdAt,
        Instant updatedAt
) {
}
