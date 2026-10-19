CREATE TABLE app_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NULL,
    rating SMALLINT,
    category VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    message TEXT,
    app_version VARCHAR(30),
    platform VARCHAR(15) NOT NULL DEFAULT 'ANDROID',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_feedback_rating
        CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),

    CONSTRAINT chk_feedback_content
        CHECK (rating IS NOT NULL OR
               NULLIF(TRIM(message), '') IS NOT NULL),

    CONSTRAINT chk_feedback_category
        CHECK (category IN ('BUG', 'SUGGESTION', 'GENERAL')),

    CONSTRAINT chk_feedback_status
        CHECK (status IN ('NEW', 'REVIEWED', 'RESOLVED'))
);

CREATE INDEX idx_feedback_status_created
    ON app_feedback (status, created_at DESC);
