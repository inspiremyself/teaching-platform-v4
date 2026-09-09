-- Align t_experiment.experiment_id with JPA Lab.id (Long / BIGINT).
-- V9 created the PK as INT; child FKs already use BIGINT. MySQL ddl-auto=validate rejects INT vs Long.
ALTER TABLE t_experiment MODIFY COLUMN experiment_id BIGINT NOT NULL;
