ALTER TABLE audit_log MODIFY COLUMN user_id BIGINT NULL;

ALTER TABLE audit_log ADD COLUMN school_id BIGINT NULL;
ALTER TABLE audit_log ADD COLUMN actor_role VARCHAR(32) NULL;
ALTER TABLE audit_log ADD COLUMN trace_id VARCHAR(36) NULL;
ALTER TABLE audit_log ADD COLUMN client_ip VARCHAR(64) NULL;

UPDATE audit_log
SET school_id = (SELECT u.school_id FROM sys_user u WHERE u.id = audit_log.user_id),
    actor_role = (SELECT u.role FROM sys_user u WHERE u.id = audit_log.user_id)
WHERE user_id IS NOT NULL;

CREATE INDEX idx_audit_log_school_created ON audit_log (school_id, created_at);
CREATE INDEX idx_audit_log_action_created ON audit_log (action, created_at);
CREATE INDEX idx_audit_log_trace_id ON audit_log (trace_id);
