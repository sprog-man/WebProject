package com.xiaoliu.webporduce.image;

import java.time.Instant;
import java.util.UUID;

public record ImageJobResponse(
        UUID id,
        String sectionId,
        String path,
        String prompt,
        String size,
        String status,
        UUID assetId,
        String assetUrl,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt
) {

    static ImageJobResponse from(ImageJob job) {
        String assetUrl = job.getAssetId() == null
                ? null
                : "/api/assets/" + job.getAssetId() + "/content";
        return new ImageJobResponse(
                job.getId(),
                job.getSectionId(),
                job.getTargetPath(),
                job.getPrompt(),
                job.getImageSize(),
                job.getStatus().name(),
                job.getAssetId(),
                assetUrl,
                job.getErrorMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getFinishedAt()
        );
    }
}
