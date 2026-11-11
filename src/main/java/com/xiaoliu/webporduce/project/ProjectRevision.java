package com.xiaoliu.webporduce.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_revision")
public class ProjectRevision {

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "revision_number", nullable = false)
    private long revisionNumber;

    @Column(name = "spec_json", nullable = false, columnDefinition = "TEXT")
    private String specJson;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(length = 255)
    private String summary;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectRevision() {
    }

    public ProjectRevision(UUID projectId, long revisionNumber, String specJson, String source, String summary) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.revisionNumber = revisionNumber;
        this.specJson = specJson;
        this.source = source;
        this.summary = summary;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public long getRevisionNumber() {
        return revisionNumber;
    }

    public String getSpecJson() {
        return specJson;
    }

    public String getSource() {
        return source;
    }

    public String getSummary() {
        return summary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
