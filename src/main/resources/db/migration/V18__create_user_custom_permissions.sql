-- Create user_permissions table for custom per-user overrides
CREATE TABLE user_permissions (
    user_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_id),
    CONSTRAINT fk_user_perms_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_perms_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
);
