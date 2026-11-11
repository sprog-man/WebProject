package com.xiaoliu.webporduce.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_config")
public class AiConfig {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "provider_type", nullable = false, length = 32)
    private String providerType;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(nullable = false, length = 160)
    private String model;

    @Column(name = "api_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiKeyEncrypted;

    @Column(name = "api_key_hint", length = 16)
    private String apiKeyHint;

    @Column(name = "is_default", nullable = false)
    private boolean defaultConfig;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AiConfig() {
    }

    public AiConfig(UUID ownerId,
                    String name,
                    String providerType,
                    String baseUrl,
                    String model,
                    String apiKeyEncrypted,
                    String apiKeyHint,
                    boolean defaultConfig) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.apiKeyHint = apiKeyHint;
        this.defaultConfig = defaultConfig;
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

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public String getProviderType() {
        return providerType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public String getApiKeyEncrypted() {
        return apiKeyEncrypted;
    }

    public String getApiKeyHint() {
        return apiKeyHint;
    }

    public boolean isDefaultConfig() {
        return defaultConfig;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String name,
                       String providerType,
                       String baseUrl,
                       String model,
                       String apiKeyEncrypted,
                       String apiKeyHint,
                       boolean defaultConfig) {
        this.name = name;
        this.providerType = providerType;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKeyEncrypted = apiKeyEncrypted;
        this.apiKeyHint = apiKeyHint;
        this.defaultConfig = defaultConfig;
    }

    public void setDefaultConfig(boolean defaultConfig) {
        this.defaultConfig = defaultConfig;
    }
}
