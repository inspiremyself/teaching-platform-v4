-- 查重功能下线（docs/技术方案/2026-11-17-查重功能下线技术方案.md）。
-- 执行前备份：data/backups/2026-11-17_144110_drop-plagiarism
DROP TABLE IF EXISTS plagiarism_task;
ALTER TABLE homework_submission DROP COLUMN plagiarism_rate;
ALTER TABLE experiment_submission DROP COLUMN plagiarism_rate;
