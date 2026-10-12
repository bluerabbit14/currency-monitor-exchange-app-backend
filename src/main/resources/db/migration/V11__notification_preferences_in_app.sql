ALTER TABLE notification_preferences
    ADD COLUMN in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE notification_preferences
    DROP COLUMN sms_enabled;
