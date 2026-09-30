CREATE TABLE applications (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- An application belongs to a job; removing the job removes its application.
    job_id         UUID        NOT NULL UNIQUE REFERENCES jobs (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL DEFAULT 'SAVED',
    applied_at     DATE,
    interview_date TIMESTAMPTZ,
    notes          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT applications_status_check CHECK (status IN
        ('SAVED', 'APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'REJECTED', 'WITHDRAWN'))
);
CREATE INDEX applications_user_status_idx ON applications (user_id, status);
CREATE INDEX applications_user_created_idx ON applications (user_id, created_at DESC);
CREATE INDEX applications_user_interview_idx ON applications (user_id, interview_date)
    WHERE interview_date IS NOT NULL;

-- Every status the application has been in, so funnel metrics (e.g. "ever reached
-- interview") survive later transitions such as REJECTED.
CREATE TABLE application_status_history (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX application_status_history_app_idx
    ON application_status_history (application_id, changed_at);
