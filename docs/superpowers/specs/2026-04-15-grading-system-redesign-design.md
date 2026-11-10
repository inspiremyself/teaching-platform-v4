# 评分系统重构设计

> **历史设计文档**：本文的分值口径已被 `docs/PRD/2026-11-09-题目分值归属与组卷分值规范PRD.md`（V1.5）覆盖；题库不再有默认分值，分值只属组卷配置。

## 1. 目标

在当前模块化单体架构下，为 `teaching-platform` 引入可扩展的统一评分内核，使系统同时支持：

- 作业与考试共用题库
- 实验继续保持独立步骤配置
- 新题型可扩展
- 多选题与填空题的部分得分
- 主观题推荐评分
- 发布级自动评分开关
- 教师逐题确认 / 接受推荐分 / 事后改分
- 学生成绩可见性与评分生成解耦

本次设计不把实验、作业、考试强行抽成统一任务提交引擎，而是在三条业务闭环之上引入共享评分能力模块。

## 2. 非目标

- 不将实验并入统一题库中心
- 不把 `lab/homework/exam` 合并成通用 activity 模块
- 不在第一版引入大模型黑盒评分作为核心依赖
- 不在第一版实现 Word 导题、AI 语义判分、图片 OCR 自动批改
- 不调整 `analysis` 的聚合定位，`score_record` 仍然只保存最终成绩事实

## 3. 架构结论

采用以下边界：

- `question/`：统一题库资产中心
- `grading/`：共享评分能力中心
- `homework/`：作业业务闭环
- `exam/`：考试业务闭环
- `lab/`：实验业务闭环
- `analysis/`：结果聚合与统计消费

依赖方向：

- `homework -> question`
- `exam -> question`
- `lab -> grading`
- `homework -> grading`
- `exam -> grading`
- `analysis -> score_record`

禁止反向依赖：

- `grading` 不依赖 `lab/homework/exam`
- `question` 不依赖具体业务闭环
- `analysis` 不依赖评分策略执行

## 4. 设计模式结论

### 4.1 使用标准 Strategy Pattern

`grading/` 模块内部正式使用标准策略模式承载单题评分算法。

核心接口：

- `QuestionScoringStrategy`

典型实现：

- `SingleChoiceScoringStrategy`
- `MultipleChoiceScoringStrategy`
- `TrueFalseScoringStrategy`
- `FillBlankScoringStrategy`
- `SubjectiveRecommendationStrategy`

统一入口：

- `ScoringStrategyRegistry`
- `ScoringEngine`

### 4.2 不对整条业务流程做模板化统一

实验、作业、考试三条链路继续保留各自 service 编排，不抽象为统一提交引擎；统一的是评分能力，不是业务流程。

## 5. 数据模型设计

### 5.1 题库主表增强

现有 `question_bank` 继续保留为统一题库主表，建议扩展：

- `source_type`
- `scoring_config_json`
- `status`
- `usage_scope`

题目附件采用强结构化模型，不使用单个 JSON 列表字段作为长期方案。

### 5.2 作业与考试挂题

#### 考试

保留 `exam_question`，新增或增强：

- `question_snapshot_json`

目的：题库题后续修改不影响历史考试。

#### 作业

新增 `homework_question`：

- `homework_id`
- `question_id`（可空，来自题库时有值）
- `sort_order`
- `question_score`
- `question_snapshot_json`

作业正式题目化，不再保留“整篇提交型作业”作为独立模式。

### 5.3 实验步骤

保留 `lab_step`，不接题库，但评分配置语义向统一评分内核靠拢。

建议补充或重构：

- `scoring_config_json`
- 步骤附件能力

### 5.4 答案与评分明细

#### 考试答案 `exam_answer`

增强为支持：

- `auto_score`
- `suggested_score`
- `final_score`
- `score_source`
- `judge_detail`
- `accepted_auto_score`

#### 作业答案 `homework_answer`

新增表，支持：

- `homework_submission_id`
- `homework_question_id`
- `answer_text`
- `answer_json`
- `auto_score`
- `suggested_score`
- `final_score`
- `score_source`
- `judge_detail`
- `teacher_comment`
- `accepted_auto_score`

#### 实验答案 `lab_step_answer`

沿用现有结构，并向统一语义靠拢：

- `score` 作为当前最终分语义
- 保留 `auto_score`
- 保留 `suggested_score`
- 保留 `score_source`
- 保留 `auto_judge_detail`

### 5.5 提交主表

- `lab_submission`：保留
- `homework_submission`：从“整篇答案承载”转为“作业提交主记录 + 总分 + 查重 + 总评语”
- `exam_submission`：保留，继续承载开考态/提交态/自动交卷/总分

### 5.6 附件模型

采用强结构型：

#### 题目附件

- `question_attachment`

用于服务：

- `question_bank`
- `homework_question` 内联题
- `exam_question` 内联题
- `lab_step`

#### 学生答案附件

- `answer_attachment`

用于服务：

- `homework_answer`
- `exam_answer`
- `lab_step_answer`

这样支持：

- 多附件
- 附件类型
- 顺序
- 删除/替换
- 后续批注扩展

### 5.7 最终成绩事实表

`score_record` 继续保留，只承接：

- 业务类型
- 业务 ID
- 学生 ID
- 班级 ID
- 最终总分
- 评分时间

不保存逐题评分细节。

## 6. 评分策略设计

### 6.1 统一枚举

建议统一引入：

- `QuestionType`
  - `SINGLE_CHOICE`
  - `MULTIPLE_CHOICE`
  - `TRUE_FALSE`
  - `FILL_BLANK`
  - `SUBJECTIVE`
- `ScoreSource`
  - `AUTO`
  - `RECOMMENDED`
  - `AUTO_ACCEPTED`
  - `TEACHER`
- `ScoringDecisionMode`
  - `AUTO_FINAL`
  - `RECOMMEND_ONLY`
  - `MANUAL_ONLY`

### 6.2 统一输入输出

#### 输入

- `ScoringContext`
  - 题目定义
  - 学生答案
  - 评分配置
  - 活动类型
  - 默认分值
  - 发布级自动评分开关

#### 输出

- `ScoringResult`
  - `autoScore`
  - `recommendedScore`
  - `finalScore`
  - `scoreSource`
  - `judgeDetail`
  - `needsTeacherReview`
  - `canAutoFinalize`

### 6.3 各题型策略

#### 单选题

- 完全匹配得满分，否则 0 分

#### 判断题

- 完全匹配得满分，允许大小写/布尔别名规范化

#### 多选题

支持三种模式：

- 全对得分
- 平均部分分
- 自定义部分分

可配置：

- 错选扣分
- 少选是否按比例得分
- 最低分封顶

#### 填空题

支持：

- 按空平均给分
- 按空自定义分值
- 忽略大小写
- 忽略符号/空格
- 多个正确答案
- 同义词/别名词表

#### 主观题

第一版不做黑盒 AI 最终定分，而是输出推荐分：

- 关键词命中
- 最小长度
- 结构项命中
- 附件完整性

系统返回：

- 推荐分
- 推荐理由
- 命中与未命中点

## 7. 分数语义与状态语义

### 7.1 分数语义

- `autoScore`：规则确定的机器分
- `recommendedScore`：系统建议分
- `finalScore`：当前有效最终分

### 7.2 题目级来源

- `AUTO`
- `RECOMMENDED`
- `AUTO_ACCEPTED`
- `TEACHER`

### 7.3 提交级状态建议

建议提交记录支持表达：

- `SAVED`
- `SUBMITTED`
- `AUTO_GRADED`
- `PARTIALLY_CONFIRMED`
- `GRADED`

若后续不想增加枚举复杂度，也应至少能表达：

- 已提交
- 待教师确认
- 已完成评分

## 8. 教师确认流与自动评分发布流

### 8.1 人工确认模式（默认）

- 客观题提交后自动得出最终分
- 主观题提交后生成推荐分
- 主观题默认待教师确认
- 教师可：
  - 手动打分
  - 接受推荐分
  - 一键接受全部推荐分
- 所有需确认题处理完后，提交进入 `GRADED`

### 8.2 自动评分发布模式

- 活动发布时开启“自动评分”
- 客观题自动分直接形成当前最终分
- 主观题推荐分可直接写为当前最终分
- 系统自动汇总总分
- 教师后续仍可改分

### 8.3 核心原则

评分结果生成与学生是否可见必须解耦：

- 系统已出分 ≠ 学生立刻可见

## 9. 成绩可见性设计

建议活动级独立配置：

- 立即可见
- 教师确认后可见
- 截止后可见
- 手动统一发布后可见

考试场景建议额外支持：

- 是否可查看总分
- 是否可查看题型分
- 是否可查看标准答案
- 是否可查看教师评语

## 10. API 重签原则

### 10.1 题目管理 API 与活动挂题 API 分离

题库管理负责：

- 建题
- 编辑题
- 查询题库
- 管题目附件

活动挂题负责：

- 从题库选题
- 现场新建题
- 调整题序
- 保存题目快照

### 10.2 学生提交 API 与教师评分 API 分离

学生接口负责：

- 保存答案
- 正式提交
- 上传答案附件
- 触发自动评分或推荐评分

教师接口负责：

- 查看提交详情
- 逐题接受推荐分
- 逐题改分
- 一键接受全部推荐分
- 完成整份评分

### 10.3 逐题接口与整份接口并存

必须同时支持：

- 逐题评分操作
- 整份提交汇总与状态完成

### 10.4 成绩可见性接口独立表达

不把“是否对学生开放成绩”混在活动发布或评分接口里。

## 11. 前端交互设计结论

### 11.1 教师配题

- 双入口：从题库选题 / 现场新建题
- 新建题时显式展示“加入题库”，默认关闭
- 评分规则前置到题目配置阶段

### 11.2 教师批改台

采用“逐题确认 + 整份汇总”结构。

每题显示：

- 题干
- 标准答案/参考点
- 学生答案
- 学生附件
- 系统推荐分/自动分
- 推荐理由
- 当前最终分
- 分数来源

每题操作：

- 接受推荐分
- 手动改分
- 重置到推荐分
- 查看评分依据

整份操作：

- 一键接受全部推荐分
- 保存批改草稿
- 完成评分
- 发布当前成绩

### 11.3 学生作答页

作业与考试都进入按题作答模式。

主观题支持：

- 文本框
- 多附件上传
- 已上传附件列表

### 11.4 学生成绩反馈页

支持：

- 总分
- 每题得分
- 教师评语
- 自动评分说明

但展示内容受成绩可见性策略控制。

## 12. 对标学习通后的取舍

参考公开可观察资料，学习通可借鉴的点包括：

- 题目来源多通道
- 客观题机器优先
- 边界题型可切人工
- 成绩可见性单独控制

本项目不直接照搬学习通较重的“题库/作业库/试卷库”分裂对象体系，而采用：

- 统一题库中心
- 统一评分内核
- 三条业务闭环分开

这更适合当前模块化单体重构方向。

## 13. 风险与约束

- 作业题目化是本次最大结构变更，会影响后端模型、前端页面、测试与迁移脚本
- 主观题推荐评分第一版应保持可解释规则，避免黑盒模型导致教师不信任
- 自动评分模式必须允许教师事后改分，否则会削弱教学可控性
- 附件采用强结构化模型，第一版开发量更高，但能避免后续再重构附件子系统
- 必须坚持 migration 驱动数据库变更，不能手改基线

## 14. 结论

本次评分系统优化应采用：

- 模块化单体架构下新增 `grading/` 共享评分模块
- 在 `grading/` 中正式采用标准 Strategy Pattern
- 作业与考试共用题库，实验保留独立步骤配置
- 作业正式题目化，主观题承接原整篇提交能力
- 自动评分、推荐评分、教师确认与成绩可见性分离建模

这套方案既满足扩展性，又不违背当前项目“不把实验、作业、考试强行抽成统一任务引擎”的约束。
