ALTER TABLE sys_user ${alterModifyColumn} school_id BIGINT NULL;

INSERT INTO sys_user (id, username, password_hash, role, display_name, status, must_change_password, school_id, last_login_at, created_at, updated_at)
SELECT COALESCE(MAX(id), 0) + 1,
       'platform9001', '{noop}123456', 'PLATFORM_ADMIN', '演示平台超管', 'ACTIVE', FALSE,
       NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user;
