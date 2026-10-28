-- 实验提交行为指标预聚合表（技术方案 D7）
-- 设计要点：
-- 1. submission_id 主键：指标随提交记录一对一落库，列表页批量读，不每次现算事件表；
-- 2. event_watermark 记录已计入的最大事件 id，供懒算判定"是否有迟到事件需要重算"；
-- 3. risk_flags_json / step_dwell_json 为 JSON 文本列，承载规则标记列表与逐题停留 Map；
-- 4. FK 指向 experiment_submission：现网无删除入口，未来新增删除链路时须先处理本表（方案 §10 登记）。
-- 双方言：DDL 与 V10 同构，MySQL/H2 均可执行，无需占位符分支。
CREATE TABLE lab_submission_metrics (
  submission_id BIGINT PRIMARY KEY,
  lab_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  first_started_at TIMESTAMP NULL,
  last_event_at TIMESTAMP NULL,
  total_duration_ms BIGINT NULL,
  active_duration_ms BIGINT NULL,
  save_count INT NOT NULL DEFAULT 0,
  paste_count INT NOT NULL DEFAULT 0,
  pasted_chars INT NOT NULL DEFAULT 0,
  paste_blocked_count INT NOT NULL DEFAULT 0,
  visibility_hidden_count INT NOT NULL DEFAULT 0,
  hidden_ms BIGINT NOT NULL DEFAULT 0,
  blur_count INT NOT NULL DEFAULT 0,
  session_count INT NOT NULL DEFAULT 0,
  step_dwell_json TEXT NULL,
  risk_score INT NOT NULL DEFAULT 0,
  risk_level VARCHAR(8) NOT NULL DEFAULT 'LOW',
  risk_flags_json TEXT NULL,
  event_watermark BIGINT NOT NULL DEFAULT 0,
  computed_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_lab_sub_metrics_submission
    FOREIGN KEY (submission_id) REFERENCES experiment_submission (id)
);

-- 教师端 riskLevel 筛选（按 lab_id 收敛教师域后等值命中）
CREATE INDEX idx_lab_sub_metrics_lab_risk ON lab_submission_metrics (lab_id, risk_level);
