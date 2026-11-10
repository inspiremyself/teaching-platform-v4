-- 清除分值兼容负债：把组卷配置分值锁死在业务域内，退役「历史非法分值」兼容口径。
-- 执行前备份：data/backups/2026-11-10_085551_before-score-domain-cleanup
-- 只钳制域外值；不读写题目快照 JSON、得分表与任何成绩数据。

UPDATE exam_question SET question_score = 1 WHERE question_score IS NULL;
UPDATE homework_question SET question_score = 1 WHERE question_score IS NULL;
UPDATE exam_question SET question_score = LEAST(100, GREATEST(1, ROUND(question_score)))
 WHERE question_score < 1 OR question_score > 100 OR question_score <> FLOOR(question_score);
UPDATE homework_question SET question_score = LEAST(100, GREATEST(1, ROUND(question_score)))
 WHERE question_score < 1 OR question_score > 100 OR question_score <> FLOOR(question_score);
UPDATE t_experiment_item SET experiment_item_score = 0 WHERE experiment_item_score IS NULL;
UPDATE t_experiment_item SET experiment_item_score = LEAST(100, GREATEST(0, experiment_item_score))
 WHERE experiment_item_score < 0 OR experiment_item_score > 100;

ALTER TABLE exam_question ADD CONSTRAINT ck_exam_question_score_domain
 CHECK (question_score >= 1 AND question_score <= 100 AND question_score = FLOOR(question_score));
ALTER TABLE homework_question ADD CONSTRAINT ck_homework_question_score_domain
 CHECK (question_score >= 1 AND question_score <= 100 AND question_score = FLOOR(question_score));
ALTER TABLE t_experiment_item ADD CONSTRAINT ck_experiment_item_score_domain
 CHECK (experiment_item_score >= 0 AND experiment_item_score <= 100);
