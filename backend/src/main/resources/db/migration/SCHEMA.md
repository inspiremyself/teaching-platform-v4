# 数据库最终版表结构说明

> 口径覆盖声明（2026-11-10 PRD V1.5 覆盖）：本文件题库编码唯一性口径以 `docs/PRD/2026-11-10-题库编码作用域收敛与发号规则PRD.md` 与 `docs/技术方案/2026-11-10-题库编码作用域收敛与发号规则技术方案.md` 为准。

> 本文档按**最终业务版数据库口径**整理，而不是按迁移过程中所有历史/过渡表逐个罗列。
>
> 也就是说：
> - 这里只保留系统最终使用的表结构；
> - 像 `lab`、`lab_step`、`lab_submission`、`lab_step_answer` 这类在迁移过程中被新实验体系替代、但 SQL 中未物理删除的旧表，**不计入最终版文档**；
> - 如果严格按 Flyway 全量执行后的“物理库”看，旧表可能仍然存在，但它们属于遗留表，不是这里要表达的“最终版数据库”。

---

## 一、最终版数据库包含的表

### 1. 用户与班级基础表

#### `sys_user`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `username` | `VARCHAR(64)` | 否 |  | 用户名，唯一 |
| `password_hash` | `VARCHAR(255)` | 否 |  | 密码哈希 |
| `role` | `VARCHAR(16)` | 否 |  | 角色 |
| `display_name` | `VARCHAR(128)` | 否 |  | 显示名称 |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `must_change_password` | `BOOLEAN` | 否 | `FALSE` | 是否强制修改密码 |
| `school_id` | `BIGINT` | 是 |  | NULL 仅表示平台级账号（无学校归属），不代表任何学校；其余为所属学校 id |
| `last_login_at` | `TIMESTAMP` | 是 |  | 最近登录时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `email` | `VARCHAR(128)` | 是 |  | 邮箱 |
| `phone` | `VARCHAR(32)` | 是 |  | 电话 |
| `office_hours` | `VARCHAR(255)` | 是 |  | 办公时间 |
| `bio` | `VARCHAR(1000)` | 是 |  | 简介 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (school_id, username)`

#### `class_room`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `name` | `VARCHAR(128)` | 否 |  | 班级名称 |
| `code` | `VARCHAR(64)` | 否 |  | 班级编码，唯一 |
| `teacher_user_id` | `BIGINT` | 否 |  | 教师 ID |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (code)`

#### `class_member`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `class_id` | `BIGINT` | 否 |  | 班级 ID |
| `student_user_id` | `BIGINT` | 否 |  | 学生 ID |
| `joined_at` | `TIMESTAMP` | 否 |  | 加入时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_class_student UNIQUE (class_id, student_user_id)`

### 2. 通用业务支撑表

#### `audit_log`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `user_id` | `BIGINT` | 否 |  | 用户 ID |
| `action` | `VARCHAR(64)` | 否 |  | 操作 |
| `resource_type` | `VARCHAR(64)` | 否 |  | 资源类型 |
| `resource_id` | `BIGINT` | 是 |  | 资源 ID |
| `detail_json` | `TEXT` | 是 |  | 详情 JSON |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `course_material`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `title` | `VARCHAR(128)` | 否 |  | 标题 |
| `category` | `VARCHAR(64)` | 否 |  | 分类 |
| `description` | `TEXT` | 是 |  | 描述 |
| `file_path` | `VARCHAR(255)` | 否 |  | 文件路径 |
| `file_name` | `VARCHAR(255)` | 否 |  | 文件名 |
| `uploader_user_id` | `BIGINT` | 否 |  | 上传者 |
| `visibility` | `VARCHAR(16)` | 否 |  | 可见范围 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `question_bank`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `code` | `VARCHAR(64)` | 否 |  | 题目编码；与 `school_id`、`created_by` 组成归属维度唯一键 |
| `type` | `VARCHAR(32)` | 否 |  | 题型 |
| `stem` | `TEXT` | 否 |  | 题干 |
| `difficulty` | `VARCHAR(32)` | 否 |  | 难度 |
| `options_json` | `TEXT` | 是 |  | 选项配置 |
| `answer_json` | `TEXT` | 是 |  | 标准答案 |
| `analysis_text` | `TEXT` | 是 |  | 解析 |
| `created_by` | `BIGINT` | 否 |  | 创建人 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `scoring_config_json` | `TEXT` | 是 |  | 判分配置 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (school_id, created_by, code)`（V29）、`UNIQUE (school_id, source_ref)`（V29）

> V29：行内 UNIQUE(code) 与 uk_question_bank_source_ref 已删除，唯一键收敛为归属维度，零回填。

#### `question_attachment`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `owner_type` | `VARCHAR(32)` | 否 |  | 所属对象类型 |
| `owner_id` | `BIGINT` | 否 |  | 所属对象 ID |
| `file_name` | `VARCHAR(255)` | 否 |  | 文件名 |
| `file_path` | `VARCHAR(512)` | 否 |  | 文件路径 |
| `media_type` | `VARCHAR(128)` | 是 |  | 媒体类型 |
| `sort_order` | `INT` | 否 |  | 排序 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

### 3. 作业模块最终表

#### `homework`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `title` | `VARCHAR(128)` | 否 |  | 标题 |
| `description` | `TEXT` | 是 |  | 描述 |
| `class_id` | `BIGINT` | 否 |  | 班级 ID |
| `start_at` | `TIMESTAMP` | 是 |  | 开始时间 |
| `due_at` | `TIMESTAMP` | 是 |  | 截止时间 |
| `attachment_path` | `VARCHAR(255)` | 是 |  | 附件路径 |
| `created_by` | `BIGINT` | 否 |  | 创建人 |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `score_visibility_mode` | `VARCHAR(32)` | 否 | `AFTER_TEACHER_CONFIRM` | 成绩可见模式 |
| `score_released` | `BOOLEAN` | 否 | `FALSE` | 是否已发布成绩 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `homework_question`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `homework_id` | `BIGINT` | 否 |  | 作业 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID |
| `sort_order` | `INT` | 否 |  | 排序 |
| `question_score` | `DOUBLE` | 否 |  | 分值（1–100 整数，V28 检查约束） |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 检查约束：`ck_homework_question_score_domain CHECK (question_score >= 1 AND question_score <= 100 AND question_score = FLOOR(question_score))`（V28）

#### `homework_submission`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `homework_id` | `BIGINT` | 否 |  | 作业 ID |
| `student_id` | `BIGINT` | 否 |  | 学生 ID |
| `submit_status` | `VARCHAR(16)` | 否 |  | 提交状态 |
| `answer_text` | `TEXT` | 是 |  | 整体答案 |
| `answer_file_path` | `VARCHAR(255)` | 是 |  | 附件路径 |
| `plagiarism_rate` | `DOUBLE` | 是 |  | 查重率 |
| `total_score` | `DOUBLE` | 是 |  | 总分 |
| `teacher_comment` | `TEXT` | 是 |  | 教师评语 |
| `graded_at` | `TIMESTAMP` | 是 |  | 批改时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_homework_submission UNIQUE (homework_id, student_id)`

#### `homework_answer`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `homework_submission_id` | `BIGINT` | 否 |  | 提交记录 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID |
| `homework_question_id` | `BIGINT` | 是 |  | 作业题目 ID |
| `answer_json` | `TEXT` | 是 |  | JSON 答案 |
| `answer_text` | `TEXT` | 是 |  | 文本答案 |
| `auto_score` | `DOUBLE` | 是 |  | 自动评分 |
| `suggested_score` | `DOUBLE` | 是 |  | 建议分数 |
| `score_source` | `VARCHAR(32)` | 否 | `TEACHER` | 评分来源 |
| `judge_detail` | `TEXT` | 是 |  | 判分详情 |
| `accepted_auto_score` | `BOOLEAN` | 否 | `FALSE` | 是否接受自动评分 |
| `score` | `DOUBLE` | 是 |  | 最终分数 |
| `teacher_comment` | `TEXT` | 是 |  | 教师评语 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

### 4. 考试模块最终表

#### `exam`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `title` | `VARCHAR(128)` | 否 |  | 标题 |
| `description` | `TEXT` | 是 |  | 描述 |
| `class_id` | `BIGINT` | 否 |  | 班级 ID |
| `start_at` | `TIMESTAMP` | 是 |  | 开始时间 |
| `end_at` | `TIMESTAMP` | 是 |  | 结束时间 |
| `duration_minutes` | `INT` | 否 |  | 时长（分钟） |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `created_by` | `BIGINT` | 否 |  | 创建人 |
| `score_visibility_mode` | `VARCHAR(32)` | 否 | `AFTER_TEACHER_CONFIRM` | 成绩可见模式 |
| `score_released` | `BOOLEAN` | 否 | `FALSE` | 是否已发布成绩 |
| `shuffle_questions` | `BOOLEAN` | 否 | `FALSE` | 是否按学生维度乱序展示题目 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `exam_question`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `exam_id` | `BIGINT` | 否 |  | 考试 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID；历史数据可能为空（内联题已停用，新数据必填） |
| `sort_order` | `INT` | 否 |  | 排序 |
| `question_score` | `DOUBLE` | 否 |  | 分值（1–100 整数，V28 检查约束） |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 检查约束：`ck_exam_question_score_domain CHECK (question_score >= 1 AND question_score <= 100 AND question_score = FLOOR(question_score))`（V28）

#### `exam_submission`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `exam_id` | `BIGINT` | 否 |  | 考试 ID |
| `student_id` | `BIGINT` | 否 |  | 学生 ID |
| `started_at` | `TIMESTAMP` | 是 |  | 开始时间 |
| `submitted_at` | `TIMESTAMP` | 是 |  | 提交时间 |
| `deadline_at` | `TIMESTAMP` | 是 |  | 截止时间 |
| `auto_score` | `DOUBLE` | 是 |  | 自动得分 |
| `manual_score` | `DOUBLE` | 是 |  | 人工得分 |
| `total_score` | `DOUBLE` | 是 |  | 总分 |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_exam_submission UNIQUE (exam_id, student_id)`

#### `exam_answer`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `exam_submission_id` | `BIGINT` | 否 |  | 提交记录 ID |
| `question_id` | `BIGINT` | 否 |  | 题目 ID |
| `answer_json` | `TEXT` | 是 |  | JSON 答案 |
| `is_correct` | `BOOLEAN` | 是 |  | 是否正确 |
| `auto_score` | `DOUBLE` | 是 |  | 自动评分 |
| `suggested_score` | `DOUBLE` | 是 |  | 建议分数 |
| `score_source` | `VARCHAR(32)` | 否 | `TEACHER` | 评分来源 |
| `judge_detail` | `TEXT` | 是 |  | 判分详情 |
| `accepted_auto_score` | `BOOLEAN` | 否 | `FALSE` | 是否接受自动评分 |
| `score` | `DOUBLE` | 是 |  | 最终得分 |
| `teacher_comment` | `TEXT` | 是 |  | 教师评语 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

### 5. 实验模块最终表

#### `t_experiment`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `experiment_id` | `BIGINT` | 否 | 自增 | 主键（与 JPA `Lab.id` / Long 对齐；V15 自 INT 升格） |
| `experiment_no` | `INT` | 否 |  | 实验编号 |
| `experiment_name` | `VARCHAR(30)` | 否 |  | 实验名称 |
| `experiment_type` | `INT` | 是 |  | 实验类型 |
| `instruction_type` | `VARCHAR(10)` | 是 |  | 指导类型 |
| `experiment_requirement` | `TEXT` | 是 |  | 实验要求 |
| `experiment_content` | `TEXT` | 是 |  | 实验内容 |
| `state` | `INT` | 是 |  | 状态码 |
| `status` | `VARCHAR(16)` | 否 | `DRAFT` | 业务状态 |
| `start_at` | `TIMESTAMP` | 是 |  | 开始时间 |
| `end_at` | `TIMESTAMP` | 是 |  | 结束时间 |
| `material_id` | `BIGINT` | 是 |  | 资料 ID |
| `class_id` | `BIGINT` | 是 |  | 班级 ID |
| `created_by` | `BIGINT` | 是 |  | 创建人 |
| `score_visibility_mode` | `VARCHAR(32)` | 否 | `AFTER_TEACHER_CONFIRM` | 成绩可见模式 |
| `score_released` | `BOOLEAN` | 否 | `FALSE` | 是否已发布成绩 |
| `summary_required` | `BOOLEAN` | 否 | `FALSE` | 是否要求实验总结 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (experiment_id)`

#### `t_experiment_item`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 |  | 主键 |
| `experiment_id` | `BIGINT` | 否 |  | 实验 ID |
| `experiment_item_no` | `INT` | 否 |  | 题目序号 |
| `experiment_item_name` | `VARCHAR(128)` | 否 |  | 题目名称 |
| `experiment_item_type` | `INT` | 否 | `5` | 题目类型编码 |
| `experiment_item_content` | `TEXT` | 否 |  | 题目内容 |
| `experiment_item_answer` | `TEXT` | 是 |  | 参考答案 |
| `experiment_item_score` | `INT` | 否 |  | 分值（0–100 整数，V28 检查约束） |
| `state` | `INT` | 否 | `1` | 状态码 |
| `question_type` | `VARCHAR(32)` | 否 |  | 题型 |
| `answer_config_json` | `TEXT` | 是 |  | 作答配置 |
| `scoring_config_json` | `TEXT` | 是 |  | 判分配置 |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `editor_language` | `VARCHAR(32)` | 否 | `TEXT` | 编辑器语言 |
| `allow_paste` | `BOOLEAN` | 否 | `TRUE` | 是否允许粘贴 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_t_experiment_item_no UNIQUE (experiment_id, experiment_item_no)`
- 检查约束：`ck_experiment_item_score_domain CHECK (experiment_item_score >= 0 AND experiment_item_score <= 100)`（V28）

#### `experiment_submission`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `experiment_id` | `BIGINT` | 否 |  | 实验 ID |
| `student_id` | `BIGINT` | 否 |  | 学生 ID |
| `submit_status` | `VARCHAR(16)` | 否 |  | 提交状态 |
| `plagiarism_rate` | `DOUBLE` | 是 |  | 查重率 |
| `total_score` | `DOUBLE` | 是 |  | 总分 |
| `teacher_comment` | `TEXT` | 是 |  | 教师评语 |
| `summary_text` | `TEXT` | 是 |  | 实验总结 |
| `summary_attachment_path` | `VARCHAR(255)` | 是 |  | 总结附件路径 |
| `submitted_at` | `TIMESTAMP` | 是 |  | 提交时间 |
| `graded_at` | `TIMESTAMP` | 是 |  | 批改时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_experiment_submission UNIQUE (experiment_id, student_id)`

#### `experiment_answer`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `experiment_submission_id` | `BIGINT` | 否 |  | 提交记录 ID |
| `experiment_item_id` | `BIGINT` | 否 |  | 实验题目 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `answer_text` | `TEXT` | 是 |  | 文本答案 |
| `answer_json` | `TEXT` | 是 |  | JSON 答案 |
| `answer_file_path` | `VARCHAR(255)` | 是 |  | 附件路径 |
| `auto_score` | `DOUBLE` | 是 |  | 自动评分 |
| `suggested_score` | `DOUBLE` | 是 |  | 建议分数 |
| `score_source` | `VARCHAR(16)` | 否 | `TEACHER` | 评分来源 |
| `auto_judge_detail` | `TEXT` | 是 |  | 自动判分详情 |
| `score` | `DOUBLE` | 是 |  | 最终分数 |
| `teacher_comment` | `TEXT` | 是 |  | 教师评语 |
| `accepted_auto_score` | `BOOLEAN` | 否 | `FALSE` | 是否接受自动评分 |
| `editor_language` | `VARCHAR(32)` | 否 | `TEXT` | 编辑器语言 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_experiment_answer UNIQUE (experiment_submission_id, experiment_item_id)`

#### `experiment_blank_answer_override`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `experiment_id` | `BIGINT` | 否 |  | 实验 ID |
| `experiment_item_id` | `BIGINT` | 否 |  | 实验题目 ID |
| `accepted_answer` | `TEXT` | 否 |  | 可接受答案 |
| `normalized_answer` | `VARCHAR(255)` | 否 |  | 标准化答案 |
| `blank_answers_json` | `TEXT` | 是 |  | 拆分后的填空答案 |
| `created_by` | `BIGINT` | 否 |  | 创建人 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`uk_experiment_blank_override UNIQUE (experiment_id, experiment_item_id, normalized_answer)`

#### `lab_step_answer_log`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `lab_step_answer_id` | `BIGINT` | 否 |  | 实验答案 ID |
| `content` | `TEXT` | 是 |  | 自动保存内容 |
| `fill_time` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 填写时间 |

**约束 / 索引**
- 主键：`PRIMARY KEY (id)`
- 外键：`fk_lab_step_answer_log_answer FOREIGN KEY (lab_step_answer_id) REFERENCES experiment_answer(id)`
- 索引：`idx_lab_answer_log_answer_fill_time (lab_step_answer_id, fill_time)`

### 6. 其他业务表

#### `plagiarism_task`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `business_type` | `VARCHAR(16)` | 否 |  | 业务类型 |
| `business_id` | `BIGINT` | 否 |  | 业务 ID |
| `student_id` | `BIGINT` | 否 |  | 学生 ID |
| `status` | `VARCHAR(16)` | 否 |  | 状态 |
| `similarity_rate` | `DOUBLE` | 是 |  | 相似度 |
| `top_match_summary` | `VARCHAR(255)` | 是 |  | 最高匹配摘要 |
| `raw_result_json` | `TEXT` | 是 |  | 原始结果 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `score_record`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `business_type` | `VARCHAR(16)` | 否 |  | 业务类型 |
| `business_id` | `BIGINT` | 否 |  | 业务 ID |
| `student_id` | `BIGINT` | 否 |  | 学生 ID |
| `class_id` | `BIGINT` | 否 |  | 班级 ID |
| `score` | `DOUBLE` | 否 |  | 分数 |
| `graded_at` | `TIMESTAMP` | 否 |  | 评分时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id；1B V26 回填，六表唯一租户隔离列 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `user_login_session`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `user_id` | `BIGINT` | 否 |  | 用户 ID |
| `session_key` | `VARCHAR(64)` | 否 |  | 会话键，唯一 |
| `status` | `VARCHAR(16)` | 否 |  | 会话状态 |
| `last_seen_at` | `TIMESTAMP` | 是 |  | 最近活跃时间 |
| `invalidated_at` | `TIMESTAMP` | 是 |  | 失效时间 |
| `expires_at` | `TIMESTAMP` | 否 |  | 过期时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (session_key)`
- 外键：`fk_user_login_session_user FOREIGN KEY (user_id) REFERENCES sys_user(id)`

#### `subject`

平台级学科字典；`code` 全平台唯一且不可变，不硬删除。初始学科由 `V30` 写入。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `code` | `VARCHAR(64)` | 否 |  | 平台学科编码，全平台唯一、不可变 |
| `name` | `VARCHAR(128)` | 否 |  | 展示名，可改 |
| `status` | `VARCHAR(16)` | 否 |  | `ACTIVE` / `DISABLED` |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (code)`

#### `school_subject`

学校学科字典，是本校启用与本地别名层；`global_subject_id` 为 NULL 表示本校自建 / 未启用。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `school_id` | `BIGINT` | 否 |  | 所属学校 id |
| `name` | `VARCHAR(128)` | 否 |  | 学科名称，可为本校别名 |
| `code` | `VARCHAR(64)` | 否 |  | 学科编码，校内唯一；映射行锁定不可改 |
| `status` | `VARCHAR(16)` | 否 |  | `ACTIVE` / `DISABLED` |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `global_subject_id` | `BIGINT` | 是 |  | 指向 `subject.id`；NULL = 本校自建 / 未启用 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (school_id, code)`
- 唯一约束：`UNIQUE (school_id, global_subject_id)`（NULL 不参与唯一约束，一校一平台学科最多一条映射行）

### 7. 内容目录与三层归属表（V31）

> 由 `V31__content_catalog_and_ownership.sql` 一次建成（阶段 2 主方案）。多态列一律不建 DB 外键；`owner_scope` / 分域学科列 / `target_type` 的取值由应用层强制，不加 DB CHECK。表 / 列说明以本文件为单一事实来源，DDL 内只写 `-- ` 行注释。

#### `textbook`

平台级教材目录；同一学科 0..N 条，学科是归属列、不是树节点。不设启用状态列，学校是否启用由 `school_textbook` 逐条唯一承载。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `global_subject_id` | `BIGINT` | 否 |  | 指向 `subject.id` |
| `code` | `VARCHAR(64)` | 否 |  | 同学科内教材编码 |
| `title` | `VARCHAR(255)` | 否 |  | 教材名称 |
| `created_by` | `BIGINT` | 是 |  | 平台超管用户 id |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `textbook_section`

教材小节树；`parent_id` 为 NULL 表示顶层，叶子 = 不存在子行（不加 `is_leaf` 标记列）。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `textbook_id` | `BIGINT` | 否 |  | 所属教材 |
| `parent_id` | `BIGINT` | 是 |  | 父小节；NULL = 顶层 |
| `heading` | `VARCHAR(255)` | 否 |  | 小节标题 |
| `sort_order` | `INT` | 否 |  | 同级呈现次序 |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `school_textbook`

学校逐条启用教材；新增教材不自动写本表、不继承同学科其它教材的挂载行。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `school_id` | `BIGINT` | 否 |  | 租户隔离列 |
| `textbook_id` | `BIGINT` | 否 |  | 被启用的平台教材 |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `courseware`

课件主表；本期只产生 `owner_scope = PLATFORM` 的行。分域学科两列互斥、由应用层强制；`status` 单列 `DRAFT` / `PUBLISHED`，下架置回 `DRAFT`。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `owner_scope` | `VARCHAR(16)` | 否 |  | `PLATFORM` / `SCHOOL` / `TEACHER` |
| `owner_school_id` | `BIGINT` | 是 |  | `PLATFORM` 行 NULL |
| `global_subject_id` | `BIGINT` | 是 |  | 指向 `subject.id`（平台内容） |
| `school_subject_id` | `BIGINT` | 是 |  | 指向 `school_subject.id`（本校内容） |
| `title` / `subtitle` | `VARCHAR(255)` | 否 / 是 |  | 标题 / 副标题 |
| `status` | `VARCHAR(16)` | 否 | `DRAFT` | 单列状态机 |
| `published_at` | `TIMESTAMP` | 是 |  | 发布时间；写值 / 清除规则归阶段 3 |
| `created_by` | `BIGINT` | 是 |  | 创建者用户 id |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `courseware_section`

课件正文按小节分行；`content_json` 是正文唯一真相源。MySQL 侧由 `${mysqlOnly}` 升级为 `LONGTEXT NOT NULL`，H2 侧保留 `TEXT`/CLOB。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `courseware_id` | `BIGINT` | 否 |  | 所属课件 |
| `heading` | `VARCHAR(255)` | 否 |  | 小节标题 |
| `sort_order` | `INT` | 否 |  | 课件内小节次序；整份布置按此展开 |
| `content_json` | `TEXT` / `LONGTEXT` | 否 |  | 结构化正文 |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `courseware_asset`

图片 / 音频素材；只存 `data/files/` 下相对路径，不内联 base64。`kind` 取 `IMAGE` / `AUDIO`。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `courseware_id` | `BIGINT` | 否 |  | 所属课件 |
| `kind` | `VARCHAR(16)` | 否 |  | `IMAGE` / `AUDIO` |
| `file_path` | `VARCHAR(512)` | 否 |  | 相对路径 |
| `mime` | `VARCHAR(128)` | 是 |  | MIME 类型 |
| `size_bytes` | `BIGINT` | 是 |  | 文件字节数 |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `courseware_assignment`

一次布置 = 同一 `assignment_group_id` 下「目标 × 小节」的多行；顺序为布置时快照。`section_id` 为 NULL 表示整份布置。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `school_id` | `BIGINT` | 否 |  | 租户隔离列 |
| `class_id` | `BIGINT` | 否 |  | 目标所属班级；鉴权 / 按班查询唯一依据 |
| `target_type` | `VARCHAR(16)` | 否 |  | `CLASS`（整班）/ `STUDENT`（班内点名） |
| `target_id` | `BIGINT` | 否 |  | `CLASS` = `class_id`；`STUDENT` = `student_user_id` |
| `courseware_id` | `BIGINT` | 否 |  | 被布置课件 |
| `section_id` | `BIGINT` | 是 |  | NULL = 整份 |
| `assignment_group_id` | `VARCHAR(36)` | 否 |  | 服务端生成 UUID v4 |
| `display_order` | `INT` | 否 |  | 同一目标组内次序；整份 = 0 |
| `start_at` / `end_at` | `TIMESTAMP` | 是 |  | 布置时间窗 |
| `created_by` | `BIGINT` | 是 |  | 布置教师用户 id |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列；组间排序依据 |

#### `content_mount`

统一挂载关系；一个内容单元 0..N 行（0 行 = 暂不挂载）。`content_type` 取 `COURSEWARE` / `QUESTION`，`mount_type` 取 `TEXTBOOK` / `TEXTBOOK_SECTION` / `COURSEWARE_SECTION`；课件不得挂到自身小节。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `content_type` | `VARCHAR(16)` | 否 |  | 挂载主体类型 |
| `content_id` | `BIGINT` | 否 |  | 内容单元 id（多态） |
| `mount_type` | `VARCHAR(24)` | 否 |  | 目录目标类型 |
| `mount_target_id` | `BIGINT` | 否 |  | 目录目标 id（多态） |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### `content_share_grant`

平台内容按学校集合授权；同一条记录只读引用、不做跨校复制。取消授权删单行，删除平台内容时同事务清该内容全部 grant 行。

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `content_type` | `VARCHAR(16)` | 否 |  | `COURSEWARE` / `QUESTION` |
| `content_id` | `BIGINT` | 否 |  | 被授权内容 id（多态） |
| `school_id` | `BIGINT` | 否 |  | 被授权学校 |
| `granted_by` | `BIGINT` | 否 |  | 平台超管用户 id |
| `created_at` / `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 审计列 |

#### V31 对存量表的加列

| 表 | 新增列 | 说明 |
|---|---|---|
| `question_bank` | `owner_scope VARCHAR(16) NOT NULL DEFAULT 'TEACHER'`、`owner_school_id BIGINT NULL`、`subject_id BIGINT NULL` | 存量行由默认值得到 `TEACHER`，不回填；`subject_id` 指向 `school_subject.id`，历史行 NULL |
| `t_experiment` | `subject_id BIGINT NULL` | 指向 `school_subject.id`；历史行 NULL |
| `homework` | `subject_id BIGINT NULL` | 同上 |
| `exam` | `subject_id BIGINT NULL` | 同上 |
| `course_material` | `subject_id BIGINT NULL` | 同上 |

---

## 二、最终版数据库的关键约束汇总

### 显式外键

- `user_login_session.user_id -> sys_user.id`
- `lab_step_answer_log.lab_step_answer_id -> experiment_answer.id`

### 显式唯一约束

- `sys_user.username`
- `class_room.code`
- `class_member (class_id, student_user_id)`
- `question_bank (school_id, owner_scope, created_by, code)`（`uk_question_bank_scope_creator_code`，V31 换键；旧 `uk_question_bank_school_creator_code` 已删）
- `question_bank (school_id, source_ref)`
- `homework_submission (homework_id, student_id)`
- `exam_submission (exam_id, student_id)`
- `t_experiment_item (experiment_id, experiment_item_no)`
- `experiment_submission (experiment_id, student_id)`
- `experiment_answer (experiment_submission_id, experiment_item_id)`
- `experiment_blank_answer_override (experiment_id, experiment_item_id, normalized_answer)`
- `user_login_session.session_key`
- `subject (code)`
- `school_subject (school_id, code)`
- `school_subject (school_id, global_subject_id)`
- `textbook (global_subject_id, code)`（`uk_textbook_subject_code`）
- `school_textbook (school_id, textbook_id)`（`uk_school_textbook`）
- `courseware_section (courseware_id, sort_order)`（`uk_courseware_section_order`）
- `courseware_assignment (assignment_group_id, target_type, target_id, display_order)`（`uk_assignment_target_order`）
- `content_mount (content_type, content_id, mount_type, mount_target_id)`（`uk_content_mount`）
- `content_share_grant (content_type, content_id, school_id)`（`uk_content_share_grant`）

### 显式索引

- `idx_t_experiment_school_class ON t_experiment (school_id, class_id)`
- `idx_homework_school_class ON homework (school_id, class_id)`
- `idx_exam_school_class ON exam (school_id, class_id)`
- `idx_question_bank_school_created_by ON question_bank (school_id, created_by)`
- `idx_score_record_school_student ON score_record (school_id, student_id)`
- `idx_plagiarism_task_school_student ON plagiarism_task (school_id, student_id)`
- `idx_textbook_section_parent ON textbook_section (textbook_id, parent_id, sort_order)`
- `idx_courseware_scope ON courseware (owner_scope, owner_school_id)`
- `idx_courseware_global_subject ON courseware (global_subject_id)`
- `idx_courseware_school_subject ON courseware (school_subject_id)`
- `idx_courseware_asset_courseware ON courseware_asset (courseware_id)`
- `idx_assignment_school_class ON courseware_assignment (school_id, class_id, created_at)`
- `idx_assignment_target ON courseware_assignment (school_id, target_type, target_id, created_at)`
- `idx_assignment_courseware ON courseware_assignment (courseware_id)`
- `idx_content_mount_target ON content_mount (mount_type, mount_target_id)`
- `idx_content_share_grant_school ON content_share_grant (school_id, content_type)`

---

## 三、最终版说明

- 最终实验模块以 `t_experiment`、`t_experiment_item`、`experiment_submission`、`experiment_answer`、`experiment_blank_answer_override` 为准。
- `lab*` 旧表不再作为最终版数据库说明对象。
- 考试模块中 `exam_question.question_id` 历史数据可能为空（内联题已停用，新数据必填）；发布与开始考试均会阻断历史内联题。
- 作业模块中 `homework_question.question_id` 为可空，表示题目可以脱离题库快照独立存在。

---

## 四、遗留对象与已知差异

> 本小节登记 1B 学校维度收口后**仍需保留**的两类对象：生产代码已零读写的遗留表，以及仍在读写但读侧尚未按校过滤的表。除注明外，表结构与 `V1`–`V26` 迁移一字不改，清理动作由后续独立任务立项。

1. **`lab` / `lab_step` / `lab_submission` / `lab_step_answer`（`V1:73-132`）**
   自 `V9` 起由 `t_experiment` / `t_experiment_item` / `experiment_submission` / `experiment_answer` 取代；「1B 遗留缺陷与读侧加固」交付后生产代码零读写 —— `PlagiarismService` 的 3 条 LAB 原生 SQL 已改查新表，`Lab`（`lab/entity/Lab.java:22`）/ `LabStep` / `LabSubmission` / `LabStepAnswer` 四个实体的 `@Table` 已分别指向 `t_experiment` / `t_experiment_item` / `experiment_submission` / `experiment_answer`。
   结论：保留表结构，不 DROP；`V16:27-31` 的 5 行 `${mysqlOnly}` 一并保留。

   **同前缀但仍在用的两张表不得纳入下线清单**（`grep "CREATE TABLE lab" db/migration/*.sql` 共 6 张，只有上面 4 张是遗留）：
   - `lab_step_answer_log`（`V10:1`）：`LabStepAnswerLog` 实体映射，`LabAnswerService:398` 写自动保存日志，`LabServiceSupport:241` 与 `LabBehaviorMetricsService:120` 读；
   - `lab_submission_metrics`（`V20:8`）：`LabSubmissionMetrics` 实体映射，`LabReportService:911` 经 `refreshStaleMetrics` 读写。

2. **`question_attachment`（`V6:29-39`）**
   生产代码零读写，仅 `grading/GradingPersistenceTests:99-113` 的持久化往返引用（`SchemaSmokeTests` 实测不含该类断言）。
   结论：表结构与 `QuestionAttachment` 实体保留、不接入生产，待「遗留表下线任务」处理。

3. **`behavior_event`（`V19:21`）**
   `school_id BIGINT NULL`；写侧 `BehaviorIngestService` 已赋值，无历史回填、无 `(school_id, …)` 索引；读侧 `BehaviorEventRepository` 的 4 个自定义查询方法未按校过滤，上游 `labId` 均已按校限定。
   结论：本期保留现状。阶段 2 启用多校真实行为数据前，须另行评估 `NOT NULL` 收紧、历史回填、索引与仓储谓词。

---

## 五、修订记录

| 日期 | 内容 |
|---|---|
| 2026-11-10 | V29 归属维度唯一键收敛；按 2026-11-10 PRD V1.5 与技术方案修订 §一字段表、§二约束汇总 |
| 2026-11-10 | V30 新增平台学科字典 subject 表与 school_subject.global_subject_id 映射列；按阶段 2 技术方案补 §一 两段表结构与 §二 3 行唯一约束 |
| 2026-11-11 | V31 内容目录与三层归属：新增 9 张内容表、7 个显式唯一约束（含 `uk_question_bank_scope_creator_code` 换键）、10 个显式索引；`question_bank` 加 3 列，`t_experiment` / `homework` / `exam` / `course_material` 各加 `subject_id` 1 列；按阶段 2 主方案补 §一 §7 段与存量表加列说明、§二 约束 / 索引清单 |
