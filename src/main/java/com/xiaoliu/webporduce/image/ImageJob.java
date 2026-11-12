package com.xiaoliu.webporduce.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image_job")
public class ImageJob {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "section_id", nullable = false, length = 60)
    private String sectionId;

    @Column(name = "target_path", nullable = false, length = 255)
    private String targetPath;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "prompt_hash", nullable = false, length = 64)
    private String promptHash;

    @Column(name = "base_src", length = 500)
    private String baseSrc;

    @Column(name = "image_size", nullable = false, length = 32)
    private String imageSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ImageJobStatus status;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImageJob() {
    }

    public ImageJob(UUID ownerId,
                    UUID projectId,
                    UUID configId,
                    String sectionId,
                    String targetPath,
                    String prompt,
                    String promptHash,
                    String baseSrc,
                    String imageSize) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.projectId = projectId;
        this.configId = configId;
        this.sectionId = sectionId;
        this.targetPath = targetPath;
        this.prompt = prompt;
        this.promptHash = promptHash;
        this.baseSrc = baseSrc;
        this.imageSize = imageSize;
        this.status = ImageJobStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markRunning() {
        status = ImageJobStatus.RUNNING;
        startedAt = Instant.now();
        errorMessage = null;
    }

    public void markSucceeded(UUID assetId) {
        status = ImageJobStatus.SUCCEEDED;
        this.assetId = assetId;
        errorMessage = null;
        finishedAt = Instant.now();
    }

    public void markFailed(String message) {
        status = ImageJobStatus.FAILED;
        errorMessage = message;
        finishedAt = Instant.now();
    }

    public void markSuperseded(String message) {
        status = ImageJobStatus.SUPERSEDED;
        errorMessage = message;
        finishedAt = Instant.now();
    }

    public boolean isActive() {
        return status == ImageJobStatus.PENDING || status == ImageJobStatus.RUNNING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getConfigId() {
        return configId;
    }

    public String getSectionId() {
        return sectionId;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getPromptHash() {
        return promptHash;
    }

    public String getBaseSrc() {
        return baseSrc;
    }

    public String getImageSize() {
        return imageSize;
    }

    public ImageJobStatus getStatus() {
        return status;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
