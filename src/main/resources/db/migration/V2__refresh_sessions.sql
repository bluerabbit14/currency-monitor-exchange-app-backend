CREATE TABLE refresh_sessions (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at   TIMESTAMP WITH TIME ZONE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT refresh_sessions_user_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT refresh_sessions_token_hash_uq UNIQUE (token_hash)
);

CREATE INDEX refresh_sessions_user_id_idx ON refresh_sessions (user_id);
