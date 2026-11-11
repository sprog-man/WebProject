package com.xiaoliu.webporduce.project;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record ProjectSummary(
        UUID id,
        String name,
        long version,
        JsonNode spec,
        Instant createdAt,
        Instant updatedAt
) {
}
