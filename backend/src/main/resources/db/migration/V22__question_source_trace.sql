-- 题目来源追溯（技术方案 2026-11-06 TD18）
-- 设计要点：
-- 1. source_type 标记题目来源：MANUAL / HOMEWORK_INLINE / LAB_STEP，存量数据保持 NULL（历史/未知）；
-- 2. source_ref 记录来源唯一键：作业 HOMEWORK:{homeworkQuestionId}、实验 LAB:{labId}:{stepId}，
--    唯一索引保证复制幂等，多个 NULL 在 MySQL 与 H2 中均视为互不相同，不影响手工题；
-- 3. 本期不回填存量数据，也不修改 V1–V21 历史迁移。
ALTER TABLE question_bank ADD COLUMN source_type VARCHAR(20) NULL;
ALTER TABLE question_bank ADD COLUMN source_ref VARCHAR(100) NULL;

CREATE UNIQUE INDEX uk_question_bank_source_ref ON question_bank (source_ref);
CREATE INDEX idx_question_bank_source_type ON question_bank (source_type);
