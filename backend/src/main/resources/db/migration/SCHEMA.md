# 数据库最终版表结构说明

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
| `last_login_at` | `TIMESTAMP` | 是 |  | 最近登录时间 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `email` | `VARCHAR(128)` | 是 |  | 邮箱 |
| `phone` | `VARCHAR(32)` | 是 |  | 电话 |
| `office_hours` | `VARCHAR(255)` | 是 |  | 办公时间 |
| `bio` | `VARCHAR(1000)` | 是 |  | 简介 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (username)`

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
| `code` | `VARCHAR(64)` | 否 |  | 题目编码，唯一 |
| `type` | `VARCHAR(32)` | 否 |  | 题型 |
| `stem` | `TEXT` | 否 |  | 题干 |
| `difficulty` | `VARCHAR(32)` | 否 |  | 难度 |
| `default_score` | `INT` | 否 |  | 默认分值 |
| `options_json` | `TEXT` | 是 |  | 选项配置 |
| `answer_json` | `TEXT` | 是 |  | 标准答案 |
| `analysis_text` | `TEXT` | 是 |  | 解析 |
| `created_by` | `BIGINT` | 否 |  | 创建人 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |
| `scoring_config_json` | `TEXT` | 是 |  | 判分配置 |

**约束**
- 主键：`PRIMARY KEY (id)`
- 唯一约束：`UNIQUE (code)`

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

**约束**
- 主键：`PRIMARY KEY (id)`

#### `homework_question`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `homework_id` | `BIGINT` | 否 |  | 作业 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID |
| `sort_order` | `INT` | 否 |  | 排序 |
| `question_score` | `DOUBLE` | 否 |  | 分值 |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

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
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

#### `exam_question`

| 字段 | 类型 | 可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `id` | `BIGINT` | 否 | 自增 | 主键 |
| `exam_id` | `BIGINT` | 否 |  | 考试 ID |
| `question_id` | `BIGINT` | 是 |  | 题库题目 ID，可为空以支持内联题 |
| `sort_order` | `INT` | 否 |  | 排序 |
| `question_score` | `DOUBLE` | 否 |  | 分值 |
| `question_snapshot_json` | `TEXT` | 否 | `'{}'` | 题目快照 |
| `created_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 否 | `CURRENT_TIMESTAMP` | 更新时间 |

**约束**
- 主键：`PRIMARY KEY (id)`

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
| `experiment_item_score` | `INT` | 否 |  | 分值 |
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

---

## 二、最终版数据库的关键约束汇总

### 显式外键

- `user_login_session.user_id -> sys_user.id`
- `lab_step_answer_log.lab_step_answer_id -> experiment_answer.id`

### 显式唯一约束

- `sys_user.username`
- `class_room.code`
- `class_member (class_id, student_user_id)`
- `question_bank.code`
- `homework_submission (homework_id, student_id)`
- `exam_submission (exam_id, student_id)`
- `t_experiment_item (experiment_id, experiment_item_no)`
- `experiment_submission (experiment_id, student_id)`
- `experiment_answer (experiment_submission_id, experiment_item_id)`
- `experiment_blank_answer_override (experiment_id, experiment_item_id, normalized_answer)`
- `user_login_session.session_key`

---

## 三、最终版说明

- 最终实验模块以 `t_experiment`、`t_experiment_item`、`experiment_submission`、`experiment_answer`、`experiment_blank_answer_override` 为准。
- `lab*` 旧表不再作为最终版数据库说明对象。
- 考试模块中 `exam_question.question_id` 为可空，表示最终版支持内联题。
- 作业模块中 `homework_question.question_id` 为可空，表示题目可以脱离题库快照独立存在。
