-- V31 内容目录与三层归属：9 张新表。表/列说明统一用 SQL 行注释承载（MySQL / H2 都通用）；
-- 权威表结构与变更说明见 db/migration/SCHEMA.md（TD15）。不引入 MySQL 专有 COMMENT 子句。
-- 多态列一律不建 DB 外键；owner_scope / 分域学科两列的取值由应用层强制，不加 DB CHECK。
-- 只用 FlywayPlaceholdersConfig 已注入的四个占位符，本脚本只用到 ${mysqlOnly}。

-- textbook：平台级教材目录，同一学科 0..N 条教材；学科是归属列，不是树节点。
-- 不设 status 列：学校是否启用由 school_textbook 逐条唯一承载（D25），不形成第二个启用真相源。
CREATE TABLE textbook (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  global_subject_id BIGINT NOT NULL, -- 指向 subject.id（V30 平台学科字典），非空
  code VARCHAR(64) NOT NULL, -- 同学科内教材编码，与 global_subject_id 组成唯一键
  title VARCHAR(255) NOT NULL,
  created_by BIGINT NULL, -- 平台超管用户 id
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- BaseEntity 审计列
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- BaseEntity 审计列
  CONSTRAINT uk_textbook_subject_code UNIQUE (global_subject_id, code)
);

-- textbook_section：教材小节树；parent_id NULL = 顶层小节，叶子 = 无子行（不加 is_leaf 标记列）。
CREATE TABLE textbook_section (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  textbook_id BIGINT NOT NULL, -- 所属教材
  parent_id BIGINT NULL, -- NULL = 顶层；叶子判定 = 不存在 parent_id 指向本行的子行
  heading VARCHAR(255) NOT NULL, -- 小节标题
  sort_order INT NOT NULL, -- 同级呈现次序
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 承接叶子判定与同级排序；不建含 parent_id 的唯一键（NULL 不参与唯一约束，会形成伪保证）。
CREATE INDEX idx_textbook_section_parent ON textbook_section (textbook_id, parent_id, sort_order);

-- school_textbook：学校逐条启用教材；新增教材不自动写本表、不继承同学科其它教材的挂载行。
CREATE TABLE school_textbook (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  school_id BIGINT NOT NULL, -- 租户隔离列，按校启用
  textbook_id BIGINT NOT NULL, -- 被启用的平台教材
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_school_textbook UNIQUE (school_id, textbook_id)
);

-- courseware：课件主表；本期只产生 owner_scope = PLATFORM 的行（V7.2 / D19 / D20）。
-- 分域学科两列互斥、由应用层强制：PLATFORM 用 global_subject_id，SCHOOL 用 school_subject_id。
-- status 单列 DRAFT / PUBLISHED；下架 = 置回 DRAFT，不新增第三态。正文不存本表。
CREATE TABLE courseware (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  owner_scope VARCHAR(16) NOT NULL, -- PLATFORM / SCHOOL / TEACHER
  owner_school_id BIGINT NULL, -- PLATFORM 行 NULL；SCHOOL / TEACHER 行写本校 id；不加 DB CHECK
  global_subject_id BIGINT NULL, -- PLATFORM 内容与教材：指向 subject.id
  school_subject_id BIGINT NULL, -- SCHOOL 内容：指向本校 school_subject.id（含自建学科）
  title VARCHAR(255) NOT NULL,
  subtitle VARCHAR(255) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- DRAFT / PUBLISHED 单列状态机
  published_at TIMESTAMP NULL, -- 发布时间；写值 / 清除规则由阶段 3 发布方案写死（PRD §5.B），本期只建列
  created_by BIGINT NULL, -- 创建者用户 id
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 归属 / 分域学科三列各自的查询索引。
CREATE INDEX idx_courseware_scope ON courseware (owner_scope, owner_school_id);
CREATE INDEX idx_courseware_global_subject ON courseware (global_subject_id);
CREATE INDEX idx_courseware_school_subject ON courseware (school_subject_id);

-- courseware_section：课件正文按小节分行，content_json 是正文唯一真相源（courseware 不设该列）。
-- 单节 1MB = 1,048,576 UTF-8 字节上限的服务端校验归阶段 3 保存动作（R17），本期只建列。
CREATE TABLE courseware_section (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  courseware_id BIGINT NOT NULL, -- 所属课件
  heading VARCHAR(255) NOT NULL, -- 小节标题
  sort_order INT NOT NULL, -- 同一课件内小节次序；整份布置按此展开
  content_json TEXT NOT NULL, -- 双库通用建列；结构化正文（paragraphs / sentences 协议）
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_courseware_section_order UNIQUE (courseware_id, sort_order)
);
-- MySQL 侧只执行本行：LONGTEXT 突破 TEXT 的 64KB 限制；H2 侧整行被占位符注释、保留 TEXT/CLOB。
-- MODIFY COLUMN 会重写整列定义，NOT NULL 必须显式带上，否则与实体 nullable = false 冲突。
${mysqlOnly}ALTER TABLE courseware_section MODIFY COLUMN content_json LONGTEXT NOT NULL;

-- courseware_asset：图片 / 音频素材；只存相对路径，不内联 base64（R16）。
CREATE TABLE courseware_asset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  courseware_id BIGINT NOT NULL, -- 所属课件
  kind VARCHAR(16) NOT NULL, -- IMAGE / AUDIO 两值，应用层枚举
  file_path VARCHAR(512) NOT NULL, -- data/files/ 下的相对路径
  mime VARCHAR(128) NULL, -- MIME 类型，上传侧写入
  size_bytes BIGINT NULL, -- 文件字节数，上传侧写入；DB 列名唯一为 size_bytes（评估文档 §5.1 的 size 是示意简写）
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_courseware_asset_courseware ON courseware_asset (courseware_id);

-- courseware_assignment：一次布置 = 同一 assignment_group_id 下「目标 × 小节」的多行；顺序是布置时快照。
-- section_id NULL = 整份布置（每个目标 1 行、display_order = 0）；非空 = 选任意不连续子集并按数组顺序呈现。
-- 目标维度（P19 / TD19）：target_type = CLASS（整班，target_id = class_id）/ STUDENT（班内点名，target_id = student_user_id）。
-- class_id 始终是租户隔离 / 鉴权 / 按班查询的唯一依据；target_id 是多态冗余列，服务端保证与 class_id 或 class_member 一致。
-- 本期只建表，不提供编辑 / 撤销 / 删除 / 布置端点（D14 / D22）；已布置记录不校验 courseware.status。
CREATE TABLE courseware_assignment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  school_id BIGINT NOT NULL, -- 1B 租户范式，非空并按校过滤
  class_id BIGINT NOT NULL, -- 目标所属班级；鉴权与按班查询唯一依据
  target_type VARCHAR(16) NOT NULL, -- CLASS / STUDENT 两值，应用层枚举强制、不加 DB CHECK
  target_id BIGINT NOT NULL, -- CLASS = class_id；STUDENT = student_user_id；多态列不建外键
  courseware_id BIGINT NOT NULL, -- 被布置课件
  section_id BIGINT NULL, -- NULL = 整份；非空 = 选中课件小节
  assignment_group_id VARCHAR(36) NOT NULL, -- 服务端生成 UUID v4，同一次布置多行共享，请求体不携带
  display_order INT NOT NULL, -- 同一目标组内次序：选节按数组下标从 1 起，整份 = 0
  start_at TIMESTAMP NULL, -- 布置时间窗起点（可空）
  end_at TIMESTAMP NULL, -- 布置时间窗终点（可空）
  created_by BIGINT NULL, -- 布置教师用户 id
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 组间排序依据（BaseEntity 审计列）
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_assignment_target_order UNIQUE (assignment_group_id, target_type, target_id, display_order)
);
-- 教师端按班读取：ORDER BY created_at DESC, assignment_group_id, display_order ASC（见 §4.1 原生 @Query）。
CREATE INDEX idx_assignment_school_class ON courseware_assignment (school_id, class_id, created_at);
-- 学生端点名读取：STUDENT 行按 (school_id, target_type, target_id) 命中；CLASS 行走上面的按班索引。
CREATE INDEX idx_assignment_target ON courseware_assignment (school_id, target_type, target_id, created_at);
-- 删除门禁：平台课件可被多校布置，按 courseware_id 查布置引用不能带 school_id（D14 / 40900）。
CREATE INDEX idx_assignment_courseware ON courseware_assignment (courseware_id);

-- content_mount：统一挂载关系，一个内容单元 0..N 行（0 行 = 暂不挂载）。
-- content_type = COURSEWARE / QUESTION；mount_type = TEXTBOOK / TEXTBOOK_SECTION / COURSEWARE_SECTION。
-- 多态列不建 DB 外键；课件不得挂到自身 courseware_section（应用层拒绝，D23）。
CREATE TABLE content_mount (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  content_type VARCHAR(16) NOT NULL, -- COURSEWARE / QUESTION
  content_id BIGINT NOT NULL, -- 内容单元 id（多态，不建外键）
  mount_type VARCHAR(24) NOT NULL, -- TEXTBOOK / TEXTBOOK_SECTION / COURSEWARE_SECTION
  mount_target_id BIGINT NOT NULL, -- 目录目标 id（多态，不建外键）
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_content_mount UNIQUE (content_type, content_id, mount_type, mount_target_id)
);
-- 反向索引：删目录目标 / 按目标筛选内容时命中。
CREATE INDEX idx_content_mount_target ON content_mount (mount_type, mount_target_id);

-- content_share_grant：平台内容按学校集合授权；同一条记录只读引用、不做跨校复制（D20）。
-- 取消授权删单行不删内容；删除平台内容时同事务清该内容全部 grant 行。
CREATE TABLE content_share_grant (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  content_type VARCHAR(16) NOT NULL, -- COURSEWARE / QUESTION
  content_id BIGINT NOT NULL, -- 被授权内容 id（多态，不建外键）
  school_id BIGINT NOT NULL, -- 被授权学校；唯一键防重复授权
  granted_by BIGINT NOT NULL, -- 平台超管用户 id；只 PLATFORM_ADMIN 可写
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_content_share_grant UNIQUE (content_type, content_id, school_id)
);
-- 正向索引：教师 / 校管按本校查已授权内容（D21 可见性过滤）。
CREATE INDEX idx_content_share_grant_school ON content_share_grant (school_id, content_type);

-- question_bank 三层归属加列（D10）：存量行由 DEFAULT 得 TEACHER，不写 UPDATE、不回填。
ALTER TABLE question_bank ADD COLUMN owner_scope VARCHAR(16) NOT NULL DEFAULT 'TEACHER'; -- PLATFORM / SCHOOL / TEACHER
ALTER TABLE question_bank ADD COLUMN owner_school_id BIGINT NULL; -- PLATFORM 行 NULL；SCHOOL / TEACHER 行写本校 id
ALTER TABLE question_bank ADD COLUMN subject_id BIGINT NULL; -- 指向 school_subject.id；历史行 NULL，不建外键

-- 换唯一键：H2 用块内具名 DROP CONSTRAINT，MySQL 用块外 DROP INDEX，两侧各只执行一条删除。
-- 注释块范式复用 V29：MySQL 侧整块被 /* ... */ 注释；H2 侧两行占位符行变行注释、块内语句执行。
${mysqlOnly}/*
ALTER TABLE question_bank DROP CONSTRAINT uk_question_bank_school_creator_code;
${mysqlOnly}*/
${mysqlOnly}ALTER TABLE question_bank DROP INDEX uk_question_bank_school_creator_code;

-- 新键是旧键 (school_id, created_by, code) 的超集：跨教师同码并存不变、数据零改写。
ALTER TABLE question_bank ADD CONSTRAINT uk_question_bank_scope_creator_code
  UNIQUE (school_id, owner_scope, created_by, code);

-- 任务侧 subject_id（D11）：5 张表中的 4 张在此加列；question_bank 已在上文一并添加。
-- 全部可空、无默认值、不回填，历史行 NULL；取值指向 school_subject.id 由 TaskSubjectValidator 强制。
ALTER TABLE t_experiment ADD COLUMN subject_id BIGINT NULL; -- 实验
ALTER TABLE homework ADD COLUMN subject_id BIGINT NULL; -- 作业
ALTER TABLE exam ADD COLUMN subject_id BIGINT NULL; -- 考试
ALTER TABLE course_material ADD COLUMN subject_id BIGINT NULL; -- 课程资料
