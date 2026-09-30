CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    company         VARCHAR(150) NOT NULL,
    title           VARCHAR(150) NOT NULL,
    location        VARCHAR(150),
    employment_type VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME',
    job_description TEXT         NOT NULL,
    source_url      VARCHAR(2048),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT jobs_employment_type_check CHECK (employment_type IN
        ('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERNSHIP', 'TEMPORARY', 'OTHER'))
);
CREATE INDEX jobs_user_created_idx ON jobs (user_id, created_at DESC);
CREATE INDEX jobs_user_company_idx ON jobs (user_id, lower(company));
