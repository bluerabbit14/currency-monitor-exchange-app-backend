ALTER TABLE notifications
    ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN read_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX notifications_user_unread_created_idx
    ON notifications (user_id, created_at DESC)
    WHERE is_read = FALSE;
