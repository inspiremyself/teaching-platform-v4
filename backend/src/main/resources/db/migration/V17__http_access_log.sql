-- 索引拆为独立 CREATE INDEX（标准 SQL，MySQL/H2 通吃；H2 不认 CREATE TABLE 内联 INDEX）
CREATE TABLE http_access_log (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  trace_id VARCHAR(36) NOT NULL,
  user_id BIGINT NULL,
  school_id BIGINT NULL,
  user_role VARCHAR(32) NULL,
  http_method VARCHAR(16) NOT NULL,
  request_path VARCHAR(512) NOT NULL,
  query_string VARCHAR(1024) NULL,
  status_code INT NOT NULL,
  duration_ms INT NOT NULL,
  client_ip VARCHAR(64) NULL,
  user_agent VARCHAR(512) NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_http_access_log_created_at ON http_access_log (created_at);
CREATE INDEX idx_http_access_log_user_id ON http_access_log (user_id);
CREATE INDEX idx_http_access_log_trace_id ON http_access_log (trace_id);
