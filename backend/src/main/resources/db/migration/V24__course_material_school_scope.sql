-- V24: 课程资料增加学校维度，落实跨校数据隔离。
-- 1) 清理 uploader 不存在或 uploader 未绑定学校的孤儿资料：
--    这些记录无法回填 school_id，保留会导致后续 NOT NULL 失败。
DELETE FROM course_material
WHERE uploader_user_id NOT IN (
    SELECT id FROM sys_user WHERE school_id IS NOT NULL
);

-- 2) 加列（先允许 NULL，便于回填）
ALTER TABLE course_material ADD COLUMN school_id BIGINT NULL;

-- 3) 按上传者所属学校回填（V2 种子资料也在此统一回填）
UPDATE course_material
SET school_id = (
    SELECT u.school_id FROM sys_user u WHERE u.id = course_material.uploader_user_id
)
WHERE school_id IS NULL;

-- 4) 收紧为 NOT NULL（MySQL: MODIFY COLUMN / H2: ALTER COLUMN）
ALTER TABLE course_material ${alterModifyColumn} school_id BIGINT NOT NULL;

-- 5) 联合索引：学校 + 可见性，支撑列表查询
CREATE INDEX idx_course_material_school_visibility
    ON course_material (school_id, visibility);
