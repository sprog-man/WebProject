package com.xiaoliu.webporduce.image;

import java.util.List;

public record ImageJobListResponse(
        List<ImageJobResponse> jobs,
        long specVersion
) {
}
