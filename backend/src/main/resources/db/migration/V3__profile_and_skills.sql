CREATE TABLE user_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    name                VARCHAR(120),
    headline            VARCHAR(160),
    location            VARCHAR(120),
    years_of_experience INTEGER CHECK (years_of_experience BETWEEN 0 AND 60),
    summary             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE skills (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name     VARCHAR(60) NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    CONSTRAINT skills_category_check CHECK (category IN
        ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'DEVOPS', 'TESTING', 'TOOL', 'SOFT', 'OTHER'))
);
CREATE UNIQUE INDEX skills_name_lower_uk ON skills (lower(name));

CREATE TABLE user_skills (
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    skill_id    UUID        NOT NULL REFERENCES skills (id) ON DELETE CASCADE,
    proficiency VARCHAR(20) NOT NULL DEFAULT 'INTERMEDIATE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, skill_id),
    CONSTRAINT user_skills_proficiency_check CHECK (proficiency IN
        ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'))
);
CREATE INDEX user_skills_skill_idx ON user_skills (skill_id);
