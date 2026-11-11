package com.xiaoliu.webporduce.asset;

import java.util.UUID;

public record AssetResponse(
        UUID id,
        String url,
        String name,
        String mimeType,
        long size,
        Integer width,
        Integer height
) {
}
