CREATE TABLE ai_config (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(80) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    model VARCHAR(160) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    api_key_hint VARCHAR(16),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_config_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_config_owner ON ai_config (owner_id, created_at);
