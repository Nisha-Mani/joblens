CREATE TABLE interview_questions (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    job_id     UUID        NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    question   VARCHAR(500) NOT NULL,
    category   VARCHAR(20) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    skills     JSONB       NOT NULL DEFAULT '[]'::jsonb,
    notes      TEXT,
    status     VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    generated  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT interview_questions_category_check CHECK (category IN
        ('TECHNICAL', 'BEHAVIORAL', 'PROJECT', 'ROLE_SPECIFIC')),
    CONSTRAINT interview_questions_difficulty_check CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT interview_questions_status_check CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'PREPARED'))
);
CREATE INDEX interview_questions_user_job_idx ON interview_questions (user_id, job_id, created_at DESC);
CREATE INDEX interview_questions_user_status_idx ON interview_questions (user_id, status);
