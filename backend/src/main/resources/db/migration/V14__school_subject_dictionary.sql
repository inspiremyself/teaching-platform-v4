CREATE TABLE school_subject (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  school_id BIGINT NOT NULL,
  name VARCHAR(128) NOT NULL,
  code VARCHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_school_subject_school_code UNIQUE (school_id, code)
);

INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at)
SELECT DISTINCT cr.school_id, TRIM(ct.subject_name),
       CASE TRIM(ct.subject_name)
         WHEN '软件体系结构' THEN 'SE-ARCH'
         WHEN '数据库原理'   THEN 'DB-PRINCIPLE'
         WHEN '未标注学科'   THEN 'UNSPECIFIED'
       END,
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM class_teacher ct
JOIN class_room cr ON cr.id = ct.class_id
WHERE ct.role = 'SUBJECT'
  AND ct.subject_name IS NOT NULL
  AND TRIM(ct.subject_name) <> ''
  AND TRIM(ct.subject_name) IN ('软件体系结构', '数据库原理', '未标注学科');

INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at)
SELECT school_id, name,
       CONCAT('LEGACY-', LPAD(CAST(rn AS CHAR(3)), 3, '0')),
       'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
  SELECT cr.school_id AS school_id,
         TRIM(ct.subject_name) AS name,
         ROW_NUMBER() OVER (PARTITION BY cr.school_id ORDER BY TRIM(ct.subject_name)) AS rn
  FROM class_teacher ct
  JOIN class_room cr ON cr.id = ct.class_id
  WHERE ct.role = 'SUBJECT'
    AND ct.subject_name IS NOT NULL
    AND TRIM(ct.subject_name) <> ''
    AND TRIM(ct.subject_name) NOT IN ('软件体系结构', '数据库原理', '未标注学科')
) legacy_names;

ALTER TABLE class_teacher ADD COLUMN subject_id BIGINT NULL;

UPDATE class_teacher ct
SET subject_id = (
  SELECT ss.id FROM school_subject ss
  INNER JOIN class_room cr ON cr.id = ct.class_id
  WHERE ss.school_id = cr.school_id
    AND ss.name = TRIM(ct.subject_name)
)
WHERE ct.role = 'SUBJECT' AND ct.subject_name IS NOT NULL;

INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at)
SELECT DISTINCT cr.school_id, '未标注学科', 'UNSPECIFIED', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM class_teacher ct
JOIN class_room cr ON cr.id = ct.class_id
WHERE ct.role = 'SUBJECT'
  AND ct.subject_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM school_subject ss WHERE ss.school_id = cr.school_id AND ss.code = 'UNSPECIFIED'
  );

UPDATE class_teacher ct
SET subject_id = (
  SELECT ss.id FROM school_subject ss
  INNER JOIN class_room cr ON cr.id = ct.class_id
  WHERE ss.school_id = cr.school_id AND ss.code = 'UNSPECIFIED'
)
WHERE ct.role = 'SUBJECT' AND ct.subject_id IS NULL;

ALTER TABLE class_teacher DROP COLUMN subject_name;
