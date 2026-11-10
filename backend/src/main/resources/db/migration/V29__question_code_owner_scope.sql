-- 题库编码作用域收敛：归属维度唯一（PRD D2 / D5），纯结构变更、零回填。
-- 双方言：只出现 FlywayPlaceholdersConfig 已注入的 ${mysqlOnly}，不新增占位符、不新增 profile。
-- H2 侧：V1 行内 UNIQUE(code) 的约束名由 H2 自动生成，按 INFORMATION_SCHEMA 定位后动态拼接删除语句；
--        DATABASE_TO_LOWER=TRUE 下约束名以双引号保大写；V22 的 uk_question_bank_source_ref 为具名唯一索引。
-- MySQL 侧：全新库 V1 行内唯一索引名为 code，H2→MySQL 迁移库为 CONSTRAINT_C2，
--           按 information_schema.STATISTICS 定位后 DROP INDEX，不静态假定任一名字。
${mysqlOnly}/*
EXECUTE IMMEDIATE (SELECT 'ALTER TABLE question_bank DROP CONSTRAINT "' || TC.CONSTRAINT_NAME || '"'
  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS TC
  JOIN INFORMATION_SCHEMA.CONSTRAINT_COLUMN_USAGE CCU ON TC.CONSTRAINT_NAME = CCU.CONSTRAINT_NAME
  WHERE LOWER(TC.TABLE_NAME) = 'question_bank' AND TC.CONSTRAINT_TYPE = 'UNIQUE'
    AND LOWER(CCU.COLUMN_NAME) = 'code');
DROP INDEX IF EXISTS uk_question_bank_source_ref;
${mysqlOnly}*/

${mysqlOnly}SET @v29_code_index = (
${mysqlOnly}  SELECT INDEX_NAME FROM information_schema.STATISTICS
${mysqlOnly}  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_bank'
${mysqlOnly}    AND COLUMN_NAME = 'code' AND NON_UNIQUE = 0 AND SEQ_IN_INDEX = 1
${mysqlOnly}    AND INDEX_NAME <> 'PRIMARY' LIMIT 1);
${mysqlOnly}SET @v29_drop_code = IF(@v29_code_index IS NULL, 'SELECT 1',
${mysqlOnly}  CONCAT('ALTER TABLE `question_bank` DROP INDEX `', @v29_code_index, '`'));
${mysqlOnly}PREPARE v29_drop_stmt FROM @v29_drop_code;
${mysqlOnly}EXECUTE v29_drop_stmt;
${mysqlOnly}DEALLOCATE PREPARE v29_drop_stmt;
${mysqlOnly}ALTER TABLE question_bank DROP INDEX uk_question_bank_source_ref;

ALTER TABLE question_bank ADD CONSTRAINT uk_question_bank_school_creator_code UNIQUE (school_id, created_by, code);
ALTER TABLE question_bank ADD CONSTRAINT uk_question_bank_school_source_ref UNIQUE (school_id, source_ref);
