package com.xiaoliu.webporduce.chat;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String role,
        String content,
        Long revisionNumber,
        Instant createdAt
) {
    public static MessageResponse from(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRole().toLowerCase(),
                message.getContent(),
                message.getRevisionNumber(),
                message.getCreatedAt()
        );
    }
}
