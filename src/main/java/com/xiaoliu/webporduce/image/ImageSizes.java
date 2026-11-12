package com.xiaoliu.webporduce.image;

import java.util.Set;

public final class ImageSizes {

    public static final String DEFAULT = "1024x1024";

    private static final Set<String> ALLOWED = Set.of(
            "512x512",
            "1024x1024",
            "1024x1536",
            "1536x1024"
    );

    private ImageSizes() {
    }

    public static String normalize(String value) {
        String size = value == null ? "" : value.trim().toLowerCase();
        if (size.isBlank()) {
            return DEFAULT;
        }
        if (!ALLOWED.contains(size)) {
            throw new com.xiaoliu.webporduce.common.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "IMAGE_SIZE_UNSUPPORTED",
                    "不支持的图片尺寸。可用尺寸：512x512、1024x1024、1024x1536、1536x1024"
            );
        }
        return size;
    }

    public static boolean isAllowed(String value) {
        return value != null && ALLOWED.contains(value.trim().toLowerCase());
    }
}
