CREATE TABLE user_favorites (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    asset_id    UUID NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT user_favorites_user_fk
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT user_favorites_asset_fk
        FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE RESTRICT,
    CONSTRAINT user_favorites_user_asset_uq UNIQUE (user_id, asset_id)
);

CREATE INDEX user_favorites_user_created_idx
    ON user_favorites (user_id, created_at DESC);
