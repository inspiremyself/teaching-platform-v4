# teacher views knowledge

## OVERVIEW

教师端页面是高密度管理工作台，不是学生端任务页的换皮版本。

当前目录形态（既定）：平铺页面 + `exams/` 子目录并存；`components/` 下保留行为展示组件与 `typed-editor/`。本期不再把其它页面子目录化。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 教师工作台 | `Dashboard.vue` | KPI、提醒、入口收口 |
| 成绩分析 | `Analysis.vue` | 指标卡、趋势图、聚合统计 |
| 资料管理 | `Materials.vue` | 上传、更新、删除、资源控制台 |
| 题库管理 | `Questions.vue` | 题型/难度/编号等显性结构 |
| 实验管理 | `Labs.vue` | 实验列表与配置入口 |
| 实验步骤配置 | `LabSteps.vue` | 步骤与题项配置 |
| 实验报告 | `LabReports.vue` `LabReportDetail.vue` `LabReportBatchGrade.vue` `LabReportViewPage.vue` `LabItemAnswersWall.vue` | 列表、详情、批量评分、汇总与逐题作答墙 |
| 实验填空题复评 | `LabBlankRegrade.vue` `LabBlankRegradeBatch.vue` | 单条与批量复评 |
| 实验提交总览 | `LabSubmissionOverview.vue` `StudentLabSubmissions.vue` | 提交概览与学生提交 |
| 作业管理 | `Homeworks.vue` | 发布、关闭、提交流 |
| 作业提交管理 | `HomeworkSubmissions.vue` | 批改、反馈、提交明细 |
| 考试管理 | `exams/List.vue` | 创建/编辑/发布/结果入口 |
| 考试成绩 | `exams/Results.vue` | 成绩与批改 |
| 查重 | `Plagiarism.vue` | 风险列表与复核动作 |

## 规范引用

SFC 风格、`composables/` 薄壳拆分、数据流与依赖方向见 `.cursor/rules/frontend-code-style.mdc`；请求 / 类型契约见 `.cursor/rules/frontend-api-contract.mdc`；lint 与构建门禁见 `.cursor/rules/toolchain-and-test-conventions.mdc`；总纲见 `.cursor/rules/code-conventions.mdc`。下列 CONVENTIONS 只保留教师端信息架构，不复制规范条款。

## CONVENTIONS

- 页面骨架优先统一为：`page-header` / 摘要区 / 筛选工具条 / 数据区 / 详情或动作区。
- 教师端优先使用 `Summary Cards`、`Filter Toolbar`、`Dense Data Table`、`Status Chips`。
- 管理页先保证操作效率、信息并列比较、批量处理路径，再考虑装饰层。
- 操作成功反馈普遍使用 `ElMessage.success`；延续现有即时反馈模式。
- 教师端页面统一依赖 `@/api/` 下的领域 API 文件，不要在页面里拼接接口路径。
- 只有 `exams/` 完成子目录化，其余页面保持平铺；改动前以实际文件为准，不按推测的子目录路径引用。

## ANTI-PATTERNS

- 不要把教师页做成低信息密度的卡片秀场。
- 不要删除筛选、状态、上下文信息来换取所谓简洁。
- 不要把实验/作业/考试的创建区与管理区混成一块无层次表单。
- 不要让重要动作只剩裸跳转按钮；要保上下文和结果反馈。
- 不要照搬学生端的“单任务主按钮”节奏到教师管理页。
