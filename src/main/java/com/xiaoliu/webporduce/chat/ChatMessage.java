package com.xiaoliu.webporduce.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    public enum Role {
        USER,
        ASSISTANT
    }

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 24)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "revision_number")
    private Long revisionNumber;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(UUID projectId, Role role, String content, Long revisionNumber) {
        this(projectId, role, content, revisionNumber, Instant.now());
    }

    public ChatMessage(UUID projectId, Role role, String content, Long revisionNumber, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.role = role.name();
        this.content = content;
        this.revisionNumber = revisionNumber;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Long getRevisionNumber() {
        return revisionNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
