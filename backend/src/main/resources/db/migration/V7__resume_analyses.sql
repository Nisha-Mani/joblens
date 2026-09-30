CREATE TABLE resume_analyses (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id                UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    -- The analysis stays useful after its resume version is deleted, so the link is optional.
    resume_id             UUID         REFERENCES resumes (id) ON DELETE SET NULL,
    resume_version        INTEGER      NOT NULL,
    overall_score         INTEGER      NOT NULL CHECK (overall_score BETWEEN 0 AND 100),
    matching_skills       JSONB        NOT NULL,
    missing_skills        JSONB        NOT NULL,
    keyword_gaps          JSONB        NOT NULL,
    experience_assessment TEXT         NOT NULL,
    suggestions           JSONB        NOT NULL,
    interview_topics      JSONB        NOT NULL,
    model                 VARCHAR(100) NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX resume_analyses_job_created_idx ON resume_analyses (job_id, created_at DESC);
CREATE INDEX resume_analyses_user_created_idx ON resume_analyses (user_id, created_at DESC);
