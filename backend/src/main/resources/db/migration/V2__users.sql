CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'))
);

-- Emails are compared case-insensitively.
CREATE UNIQUE INDEX users_email_lower_uk ON users (lower(email));
