package com.xiaoliu.webporduce.project;

import java.time.Instant;

public record SaveDraftResponse(
        long version,
        Instant updatedAt
) {
}
