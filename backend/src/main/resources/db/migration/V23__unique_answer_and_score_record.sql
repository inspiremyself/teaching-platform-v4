-- V23：为 exam_answer 与 score_record 增加幂等唯一约束。
-- 执行约定：先去重、后建约束；迁移前必须执行
-- ./scripts/common/backup-local-data.sh --label p0-unique-constraints --with-files
-- 去重规则：每个业务键分组只保留 id 最大的一行（最后一次写入），其余删除。
-- 派生表包裹同表子查询，兼容 MySQL 8 与 H2 2.x。

-- 1) exam_answer 去重：保留每个 (exam_submission_id, question_id) 分组中 id 最大的一行
DELETE FROM exam_answer
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id
        FROM exam_answer
        GROUP BY exam_submission_id, question_id
    ) keep_rows
);

ALTER TABLE exam_answer
    ADD CONSTRAINT uk_exam_answer_submission_question
    UNIQUE (exam_submission_id, question_id);

-- 2) score_record 去重：保留每个 (business_type, business_id, student_id) 分组中 id 最大的一行
DELETE FROM score_record
WHERE id NOT IN (
    SELECT keep_id FROM (
        SELECT MAX(id) AS keep_id
        FROM score_record
        GROUP BY business_type, business_id, student_id
    ) keep_rows
);

ALTER TABLE score_record
    ADD CONSTRAINT uk_score_record_business_student
    UNIQUE (business_type, business_id, student_id);
