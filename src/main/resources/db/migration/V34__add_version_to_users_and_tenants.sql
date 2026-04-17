-- V34__add_version_to_users_and_tenants.sql
ALTER TABLE users ADD COLUMN version BIGINT DEFAULT 0;
ALTER TABLE tenants ADD COLUMN version BIGINT DEFAULT 0;
