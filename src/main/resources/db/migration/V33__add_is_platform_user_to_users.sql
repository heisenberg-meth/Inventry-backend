-- V33__add_is_platform_user_to_users.sql
ALTER TABLE users
ADD COLUMN is_platform_user BOOLEAN DEFAULT FALSE;
