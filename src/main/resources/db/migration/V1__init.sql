CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE project (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    spec_json TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_project_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_project_owner_updated ON project (owner_id, updated_at DESC);

CREATE TABLE project_revision (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    revision_number BIGINT NOT NULL,
    spec_json TEXT NOT NULL,
    source VARCHAR(24) NOT NULL,
    summary VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_revision_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT uq_revision_project_number UNIQUE (project_id, revision_number)
);

CREATE INDEX idx_revision_project ON project_revision (project_id, revision_number DESC);

CREATE TABLE chat_message (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    role VARCHAR(24) NOT NULL,
    content TEXT NOT NULL,
    revision_number BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_message_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_message_project_created ON chat_message (project_id, created_at);

CREATE TABLE asset (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    project_id UUID,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    byte_size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_asset_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_asset_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE SET NULL
);

CREATE INDEX idx_asset_owner ON asset (owner_id, created_at DESC);
