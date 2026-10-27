-- H2 → MySQL 整库导入后，IDENTITY/AUTO_INCREMENT 常丢失（见 scripts/common/h2-dump-to-mysql.py）。
-- JPA BaseEntity 使用 GenerationType.IDENTITY；无 AUTO_INCREMENT 时 INSERT 报
-- "Field 'id' doesn't have a default value"。
-- MySQL 不允许直接 MODIFY 被外键引用的列，故临时关闭外键检查。
-- 双方言占位符（spring.flyway.placeholders）：
--   MySQL 值: fkChecksDisable=SET FOREIGN_KEY_CHECKS = 0；fkChecksEnable=SET FOREIGN_KEY_CHECKS = 1；alterModifyColumn=MODIFY COLUMN（两个词）
--   H2:    fkChecksDisable=SET REFERENTIAL_INTEGRITY FALSE / fkChecksEnable=SET REFERENTIAL_INTEGRITY TRUE / alterModifyColumn=ALTER COLUMN

${fkChecksDisable};

${mysqlOnly}ALTER TABLE audit_log ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE class_member ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE class_room ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE class_teacher ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE course_material ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE exam ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE exam_answer ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE exam_question ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE exam_submission ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE experiment_answer ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE experiment_blank_answer_override ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE experiment_submission ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE homework ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE homework_answer ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE homework_question ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE homework_submission ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE lab ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE lab_step ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE lab_step_answer ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE lab_step_answer_log ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE lab_submission ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE plagiarism_task ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE question_attachment ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE question_bank ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE school ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE school_subject ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE score_record ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE sys_user ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE t_experiment ${alterModifyColumn} experiment_id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE t_experiment_item ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;
${mysqlOnly}ALTER TABLE user_login_session ${alterModifyColumn} id BIGINT NOT NULL AUTO_INCREMENT;

${fkChecksEnable};
