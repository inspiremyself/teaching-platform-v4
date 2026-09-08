CREATE TABLE school (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  code VARCHAR(64) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO school (id, name, code, status, created_at, updated_at)
VALUES (1, '演示学校', 'DEMO-SCHOOL', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE sys_user ADD COLUMN school_id BIGINT;
UPDATE sys_user SET school_id = 1;
ALTER TABLE sys_user ALTER COLUMN school_id SET NOT NULL;

ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS UK_SYS_USER_USERNAME;
ALTER TABLE sys_user DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_2;
ALTER TABLE sys_user ADD CONSTRAINT uk_sys_user_school_username UNIQUE (school_id, username);

ALTER TABLE class_room ADD COLUMN school_id BIGINT;
UPDATE class_room SET school_id = 1;
ALTER TABLE class_room ALTER COLUMN school_id SET NOT NULL;

ALTER TABLE class_room DROP CONSTRAINT IF EXISTS UK_CLASS_ROOM_CODE;
ALTER TABLE class_room DROP CONSTRAINT IF EXISTS CONSTRAINT_INDEX_3;
ALTER TABLE class_room ADD CONSTRAINT uk_class_room_school_code UNIQUE (school_id, code);

CREATE TABLE class_teacher (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  class_id BIGINT NOT NULL,
  teacher_user_id BIGINT NOT NULL,
  role VARCHAR(16) NOT NULL,
  subject_name VARCHAR(64) NULL,
  assigned_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_class_teacher UNIQUE (class_id, teacher_user_id)
);

INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_name, assigned_at, created_at, updated_at)
SELECT id, teacher_user_id, 'SUBJECT', '未标注学科', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM class_room
WHERE teacher_user_id IS NOT NULL;

ALTER TABLE class_room DROP COLUMN teacher_user_id;

ALTER TABLE class_member ADD CONSTRAINT uk_class_member_student UNIQUE (student_user_id);

INSERT INTO sys_user (id, username, password_hash, role, display_name, status, must_change_password, school_id, last_login_at, created_at, updated_at)
VALUES (4, 'admin9001', '{noop}123456', 'SCHOOL_ADMIN', '演示校管', 'ACTIVE', FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_user (id, username, password_hash, role, display_name, status, must_change_password, school_id, last_login_at, created_at, updated_at)
VALUES (5, 't9002', '{noop}123456', 'TEACHER', '李老师', 'ACTIVE', FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO class_teacher (class_id, teacher_user_id, role, subject_name, assigned_at, created_at, updated_at)
VALUES (1, 5, 'SUBJECT', '数据库原理', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE class_teacher
SET subject_name = '软件体系结构'
WHERE class_id = 1
  AND teacher_user_id = (SELECT id FROM sys_user WHERE username = 't9001' AND role = 'TEACHER');
