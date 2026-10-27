-- Align t_experiment.experiment_id with JPA Lab.id (Long / BIGINT).
-- V9 created the PK as INT; child FKs already use BIGINT. MySQL ddl-auto=validate rejects INT vs Long.
-- ${alterModifyColumn} 由 spring.flyway.placeholders 注入：MySQL=MODIFY COLUMN，H2=ALTER COLUMN。
ALTER TABLE t_experiment ${alterModifyColumn} experiment_id BIGINT NOT NULL;
