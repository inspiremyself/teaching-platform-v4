-- V26: 六张存量教学业务表补 school_id，按 V24 五步范式收口。
-- 1) 清孤儿：来源 JOIN 不到归属记录、归属学校为空、归属学校不在 school 表、class_id 为空。
DELETE FROM t_experiment
WHERE class_id IS NULL
   OR class_id NOT IN (
       SELECT cr.id FROM class_room cr JOIN school s ON s.id = cr.school_id
       WHERE cr.school_id IS NOT NULL
   );

DELETE FROM homework
WHERE class_id IS NULL
   OR class_id NOT IN (
       SELECT cr.id FROM class_room cr JOIN school s ON s.id = cr.school_id
       WHERE cr.school_id IS NOT NULL
   );

DELETE FROM exam
WHERE class_id IS NULL
   OR class_id NOT IN (
       SELECT cr.id FROM class_room cr JOIN school s ON s.id = cr.school_id
       WHERE cr.school_id IS NOT NULL
   );

DELETE FROM question_bank
WHERE created_by NOT IN (
    SELECT u.id FROM sys_user u JOIN school s ON s.id = u.school_id
    WHERE u.school_id IS NOT NULL
);

DELETE FROM score_record
WHERE class_id IS NULL
   OR class_id NOT IN (
       SELECT cr.id FROM class_room cr JOIN school s ON s.id = cr.school_id
       WHERE cr.school_id IS NOT NULL
   );

DELETE FROM plagiarism_task
WHERE student_id NOT IN (
    SELECT u.id FROM sys_user u JOIN school s ON s.id = u.school_id
    WHERE u.school_id IS NOT NULL
);

-- 2) 加列（先允许 NULL，便于回填）
ALTER TABLE t_experiment ADD COLUMN school_id BIGINT NULL;
ALTER TABLE homework ADD COLUMN school_id BIGINT NULL;
ALTER TABLE exam ADD COLUMN school_id BIGINT NULL;
ALTER TABLE question_bank ADD COLUMN school_id BIGINT NULL;
ALTER TABLE score_record ADD COLUMN school_id BIGINT NULL;
ALTER TABLE plagiarism_task ADD COLUMN school_id BIGINT NULL;

-- 3) 按唯一来源回填（沿用 V24 的无表别名 UPDATE 形态，兼容 H2 / MySQL）
UPDATE t_experiment
SET school_id = (SELECT cr.school_id FROM class_room cr WHERE cr.id = t_experiment.class_id)
WHERE school_id IS NULL;

UPDATE homework
SET school_id = (SELECT cr.school_id FROM class_room cr WHERE cr.id = homework.class_id)
WHERE school_id IS NULL;

UPDATE exam
SET school_id = (SELECT cr.school_id FROM class_room cr WHERE cr.id = exam.class_id)
WHERE school_id IS NULL;

UPDATE question_bank
SET school_id = (SELECT u.school_id FROM sys_user u WHERE u.id = question_bank.created_by)
WHERE school_id IS NULL;

UPDATE score_record
SET school_id = (SELECT cr.school_id FROM class_room cr WHERE cr.id = score_record.class_id)
WHERE school_id IS NULL;

UPDATE plagiarism_task
SET school_id = (SELECT u.school_id FROM sys_user u WHERE u.id = plagiarism_task.student_id)
WHERE school_id IS NULL;

-- 4) 派生表 ① 类：主表存在且与业务主表 school_id 不一致的行按孤儿处理。
--    score_record.business_id 直查 exam / homework / t_experiment.experiment_id；
--    plagiarism_task.business_id 经 homework_submission / experiment_submission / exam_submission 反查，
--    LAB 分支只查运行时的 experiment_submission，不查遗留 lab_submission。
DELETE FROM score_record
WHERE business_type = 'EXAM' AND business_id IN (SELECT e.id FROM exam e)
  AND school_id <> (SELECT e.school_id FROM exam e WHERE e.id = score_record.business_id);

DELETE FROM score_record
WHERE business_type = 'HOMEWORK' AND business_id IN (SELECT h.id FROM homework h)
  AND school_id <> (SELECT h.school_id FROM homework h WHERE h.id = score_record.business_id);

DELETE FROM score_record
WHERE business_type = 'LAB' AND business_id IN (SELECT e.experiment_id FROM t_experiment e)
  AND school_id <> (SELECT e.school_id FROM t_experiment e WHERE e.experiment_id = score_record.business_id);

DELETE FROM plagiarism_task
WHERE business_type = 'HOMEWORK'
  AND business_id IN (
      SELECT hs.id FROM homework_submission hs JOIN homework h ON h.id = hs.homework_id
  )
  AND school_id <> (
      SELECT h.school_id FROM homework_submission hs JOIN homework h ON h.id = hs.homework_id
      WHERE hs.id = plagiarism_task.business_id
  );

DELETE FROM plagiarism_task
WHERE business_type = 'LAB'
  AND business_id IN (
      SELECT es.id FROM experiment_submission es JOIN t_experiment e ON e.experiment_id = es.experiment_id
  )
  AND school_id <> (
      SELECT e.school_id FROM experiment_submission es JOIN t_experiment e ON e.experiment_id = es.experiment_id
      WHERE es.id = plagiarism_task.business_id
  );

DELETE FROM plagiarism_task
WHERE business_type = 'EXAM'
  AND business_id IN (
      SELECT es.id FROM exam_submission es JOIN exam e ON e.id = es.exam_id
  )
  AND school_id <> (
      SELECT e.school_id FROM exam_submission es JOIN exam e ON e.id = es.exam_id
      WHERE es.id = plagiarism_task.business_id
  );

-- 5) 派生表 ① 类等价性对齐：业务主表 school_id 是唯一真相；② 类（反查不到主表）不匹配下面的
--    IN 条件，保留第 3 段回填值，不删除、不对齐。
UPDATE score_record
SET school_id = (SELECT e.school_id FROM exam e WHERE e.id = score_record.business_id)
WHERE business_type = 'EXAM' AND business_id IN (SELECT e.id FROM exam e);

UPDATE score_record
SET school_id = (SELECT h.school_id FROM homework h WHERE h.id = score_record.business_id)
WHERE business_type = 'HOMEWORK' AND business_id IN (SELECT h.id FROM homework h);

UPDATE score_record
SET school_id = (SELECT e.school_id FROM t_experiment e WHERE e.experiment_id = score_record.business_id)
WHERE business_type = 'LAB' AND business_id IN (SELECT e.experiment_id FROM t_experiment e);

UPDATE plagiarism_task
SET school_id = (
    SELECT h.school_id FROM homework_submission hs JOIN homework h ON h.id = hs.homework_id
    WHERE hs.id = plagiarism_task.business_id
)
WHERE business_type = 'HOMEWORK'
  AND business_id IN (SELECT hs.id FROM homework_submission hs JOIN homework h ON h.id = hs.homework_id);

UPDATE plagiarism_task
SET school_id = (
    SELECT e.school_id FROM experiment_submission es JOIN t_experiment e ON e.experiment_id = es.experiment_id
    WHERE es.id = plagiarism_task.business_id
)
WHERE business_type = 'LAB'
  AND business_id IN (SELECT es.id FROM experiment_submission es JOIN t_experiment e ON e.experiment_id = es.experiment_id);

UPDATE plagiarism_task
SET school_id = (
    SELECT e.school_id FROM exam_submission es JOIN exam e ON e.id = es.exam_id
    WHERE es.id = plagiarism_task.business_id
)
WHERE business_type = 'EXAM'
  AND business_id IN (SELECT es.id FROM exam_submission es JOIN exam e ON e.id = es.exam_id);

-- 6) 收紧为 NOT NULL（MySQL: MODIFY COLUMN / H2: ALTER COLUMN，值由 FlywayPlaceholdersConfig 注入）
ALTER TABLE t_experiment ${alterModifyColumn} school_id BIGINT NOT NULL;
ALTER TABLE homework ${alterModifyColumn} school_id BIGINT NOT NULL;
ALTER TABLE exam ${alterModifyColumn} school_id BIGINT NOT NULL;
ALTER TABLE question_bank ${alterModifyColumn} school_id BIGINT NOT NULL;
ALTER TABLE score_record ${alterModifyColumn} school_id BIGINT NOT NULL;
ALTER TABLE plagiarism_task ${alterModifyColumn} school_id BIGINT NOT NULL;

-- 7) 联合索引：学校为前导列
CREATE INDEX idx_t_experiment_school_class ON t_experiment (school_id, class_id);
CREATE INDEX idx_homework_school_class ON homework (school_id, class_id);
CREATE INDEX idx_exam_school_class ON exam (school_id, class_id);
CREATE INDEX idx_question_bank_school_created_by ON question_bank (school_id, created_by);
CREATE INDEX idx_score_record_school_student ON score_record (school_id, student_id);
CREATE INDEX idx_plagiarism_task_school_student ON plagiarism_task (school_id, student_id);
