-- 学生作答行为事件表（P0 行为采集链）
-- 设计要点（技术方案 D1/D5）：
-- 1. 独立于 audit_log：高频批量写与管理审计低频单写是两种负载，可扩展字段一律进 payload_json；
-- 2. 事件归属键 = (biz_type, lab_id, student_id)，submission_id 仅冗余可空
--    （LAB_ENTER 到达时 submission 可能尚未创建，归属键必须在首次保存前成立）；
-- 3. 无外键：行为事件允许在 submission 创建前到达，且采集链路不能因业务行删除而级联丢数据；
-- 4. event_uuid 全表唯一：前端批量缓冲 + 失败重试必须配幂等键，重复 uuid 丢弃。
-- 双方言：DDL 与 V10 同构（AUTO_INCREMENT + TIMESTAMP + TEXT），MySQL/H2 均可执行，无需占位符分支。
CREATE TABLE behavior_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_uuid VARCHAR(36) NOT NULL,
  biz_type VARCHAR(16) NOT NULL DEFAULT 'LAB',
  event_type VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMP NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  student_id BIGINT NOT NULL,
  lab_id BIGINT NOT NULL,
  submission_id BIGINT NULL,
  step_id BIGINT NULL,
  payload_json TEXT NULL,
  school_id BIGINT NULL,
  client_ip VARCHAR(64) NULL,
  trace_id VARCHAR(36) NULL,
  CONSTRAINT uk_behavior_event_uuid UNIQUE (event_uuid)
);

-- 指标聚合按归属键 (biz_type, lab_id, student_id) 顺序扫描的核心索引
CREATE INDEX idx_behavior_event_biz_lab_student ON behavior_event (biz_type, lab_id, student_id, id);
CREATE INDEX idx_behavior_event_submission ON behavior_event (submission_id);
CREATE INDEX idx_behavior_event_type_time ON behavior_event (event_type, occurred_at);
