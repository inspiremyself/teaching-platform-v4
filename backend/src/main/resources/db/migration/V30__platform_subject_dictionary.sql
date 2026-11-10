-- 平台级学科字典与学校启用映射；纯结构变更 + 新表字典数据，存量教学数据零改写。
-- 双方言沿用 V14：BIGINT AUTO_INCREMENT PRIMARY KEY 在 H2(MODE=MySQL) 与 MySQL 均可执行。
CREATE TABLE subject (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_subject_code UNIQUE (code)
);

INSERT INTO subject (code, name, status, created_at, updated_at) VALUES
  ('MATH', '数学', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('CHINESE', '语文', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ENGLISH', '英语', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('COMPUTER', '计算机', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE school_subject ADD COLUMN global_subject_id BIGINT NULL;
ALTER TABLE school_subject
  ADD CONSTRAINT uk_school_subject_school_global UNIQUE (school_id, global_subject_id);
