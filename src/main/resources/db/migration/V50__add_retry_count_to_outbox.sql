-- V50__add_retry_count_to_outbox.sql
ALTER TABLE outbox_event ADD COLUMN retry_count INT DEFAULT 0;
