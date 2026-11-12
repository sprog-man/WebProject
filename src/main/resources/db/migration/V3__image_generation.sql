CREATE TABLE image_ai_config (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model VARCHAR(160) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    api_key_hint VARCHAR(16),
    default_size VARCHAR(32) NOT NULL,
    default_quality VARCHAR(32),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_image_ai_config_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_image_ai_config_owner ON image_ai_config (owner_id, created_at);

CREATE TABLE image_job (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID NOT NULL,
    config_id UUID NOT NULL,
    section_id VARCHAR(60) NOT NULL,
    target_path VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    prompt_hash VARCHAR(64) NOT NULL,
    image_size VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    asset_id UUID,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_image_job_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_image_job_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_image_job_config FOREIGN KEY (config_id) REFERENCES image_ai_config (id) ON DELETE CASCADE,
    CONSTRAINT fk_image_job_asset FOREIGN KEY (asset_id) REFERENCES asset (id) ON DELETE SET NULL
);

CREATE INDEX idx_image_job_project_created ON image_job (project_id, created_at DESC);
CREATE INDEX idx_image_job_project_status ON image_job (project_id, status);

ALTER TABLE asset ADD COLUMN source VARCHAR(24);
ALTER TABLE asset ADD COLUMN prompt TEXT;
