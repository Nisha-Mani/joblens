CREATE TABLE resumes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL,
    storage_key    VARCHAR(100) NOT NULL,
    extracted_text TEXT         NOT NULL,
    parsed_data    JSONB        NOT NULL,
    version        INTEGER      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT resumes_user_version_uk UNIQUE (user_id, version)
);
CREATE INDEX resumes_user_created_idx ON resumes (user_id, created_at DESC);
