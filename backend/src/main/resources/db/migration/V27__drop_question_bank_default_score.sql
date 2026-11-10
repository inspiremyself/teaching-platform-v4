-- TD1：题库不再保留任何形式的分值，删除 default_score 列（PRD D2）。
-- 执行前备份：data/backups/2026-11-09_165758_drop-question-default-score
ALTER TABLE question_bank DROP COLUMN default_score;
