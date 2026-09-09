# teacher views knowledge

## OVERVIEW

教师端页面是高密度管理工作台，不是学生端任务页的换皮版本。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 教师工作台 | `Dashboard.vue` | KPI、提醒、入口收口 |
| 成绩分析 | `Analysis.vue` | 指标卡、趋势图、聚合统计 |
| 班级管理 | `Classes.vue` | 班级列表、状态、增删改 |
| 资料管理 | `Materials.vue` | 上传、更新、删除、资源控制台 |
| 题库管理 | `Questions.vue` | 题型/难度/编号等显性结构 |
| 实验管理 | `Labs.vue` `labs/List.vue` | 现有页与旧平铺页并存 |
| 实验步骤配置 | `LabSteps.vue` `labs/Steps.vue` | 新旧两类实现并存，改前先辨认 |
| 实验报告 | `LabReports.vue` `LabReportDetail.vue` `lab-reports/` | 列表、详情、评分 |
| 作业管理 | `Homeworks.vue` `homeworks/List.vue` | 发布、关闭、提交流 |
| 作业提交管理 | `HomeworkSubmissions.vue` `homeworks/Submissions.vue` | 批改、反馈、提交明细 |
| 考试管理 | `exams/List.vue` | 创建/编辑/发布/结果入口 |
| 查重 | `Plagiarism.vue` | 风险列表与复核动作 |

## CONVENTIONS

- 页面骨架优先统一为：`page-header` / 摘要区 / 筛选工具条 / 数据区 / 详情或动作区。
- 教师端优先使用 `Summary Cards`、`Filter Toolbar`、`Dense Data Table`、`Status Chips`。
- 管理页先保证操作效率、信息并列比较、批量处理路径，再考虑装饰层。
- 操作成功反馈普遍使用 `ElMessage.success`；延续现有即时反馈模式。
- 教师端页面通常直接依赖 `@/api/teacher` 或模块 API 文件，不要在页面里拼接接口路径。
- 如果文件同时存在“扁平旧页”和“原型对齐新版”，优先沿新版模式继续收敛，不要回退到简单表单 + 裸表格。

## ANTI-PATTERNS

- 不要把教师页做成低信息密度的卡片秀场。
- 不要删除筛选、状态、上下文信息来换取所谓简洁。
- 不要把实验/作业/考试的创建区与管理区混成一块无层次表单。
- 不要让重要动作只剩裸跳转按钮；要保上下文和结果反馈。
- 不要照搬学生端的“单任务主按钮”节奏到教师管理页。
