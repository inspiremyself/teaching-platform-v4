-- 登录会话外键移除：DB 层不再建外键，参照完整性由应用层保证（配套 PRD P3）。
-- 只用 FlywayPlaceholdersConfig 已注入的 ${mysqlOnly}；复用 V29 / V31 的双方言注释块范式。
-- H2 侧执行块内具名 DROP CONSTRAINT；MySQL 侧整块被注释、执行块外 DROP FOREIGN KEY。
-- MySQL 建 FK 时自动生成的 user_id 单列索引保留不删；双侧统一补显式三列索引。
${mysqlOnly}/*
ALTER TABLE user_login_session DROP CONSTRAINT fk_user_login_session_user;
${mysqlOnly}*/
${mysqlOnly}ALTER TABLE user_login_session DROP FOREIGN KEY fk_user_login_session_user;

CREATE INDEX idx_user_login_session_user_status ON user_login_session (user_id, status, created_at);
