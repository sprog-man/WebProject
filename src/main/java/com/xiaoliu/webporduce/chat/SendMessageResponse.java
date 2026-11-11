package com.xiaoliu.webporduce.chat;

import tools.jackson.databind.JsonNode;

public record SendMessageResponse(
        MessageResponse userMessage,
        MessageResponse assistantMessage,
        JsonNode spec,
        long version,
        Long revisionNumber,
        boolean changed
) {
}
