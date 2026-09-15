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
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_http_access_log_created_at (created_at),
  INDEX idx_http_access_log_user_id (user_id),
  INDEX idx_http_access_log_trace_id (trace_id)
);
