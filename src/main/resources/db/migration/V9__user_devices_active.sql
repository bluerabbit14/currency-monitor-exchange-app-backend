ALTER TABLE user_devices
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX user_devices_user_id_active_idx
    ON user_devices (user_id, is_active);
