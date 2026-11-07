# student views knowledge

## OVERVIEW

学生端页面以任务闭环、截止时间、进度反馈为中心，必须和教师端管理视角分开。

当前目录形态（既定）：平铺页面 + `exams/` 子目录并存；`components/lab-answer/` 承载实验作答编辑器。本期不再把其它页面子目录化。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 学生工作台 | `Dashboard.vue` | 当前任务、截止时间、主入口 |
| 资料页 | `Materials.vue` | 资源获取与下载 |
| 实验列表/作答 | `Labs.vue` `LabDetail.vue` `LabSteps.vue` | 列表、详情、分步作答 |
| 实验作答编辑器 | `components/lab-answer/` | 分题型作答组件与载荷构造 |
| 作业列表/提交 | `Homeworks.vue` `HomeworkDetail.vue` | 任务型页面 |
| 考试列表/作答 | `exams/List.vue` `exams/Detail.vue` | 时间压力 + 提交语义 |
| 成绩总览 | `Scores.vue` | 反馈与结果汇总 |
| 代码作答组件 | `CodeAnswerEditor.vue` | 页面内复用的编辑器组件 |

## 规范引用

SFC 风格、`composables/` 薄壳拆分、数据流与依赖方向见 `.cursor/rules/frontend-code-style.mdc`；请求 / 类型契约见 `.cursor/rules/frontend-api-contract.mdc`；lint 与构建门禁见 `.cursor/rules/toolchain-and-test-conventions.mdc`；总纲见 `.cursor/rules/code-conventions.mdc`。下列 CONVENTIONS 只保留学生端信息架构，不复制规范条款。

## CONVENTIONS

- 学生页优先表达：当前任务、截止时间、进度、主操作入口、结果反馈。
- 主编辑区/答题区必须优先可读性，不要让视觉装饰压过输入和反馈。
- 页面成功动作同样使用即时反馈；提交、保存、交卷后要有清晰确认。
- 学生页面统一依赖 `@/api/` 下的领域 API 文件；契约变化时优先先看 API 与类型文件。
- 页面信息架构变更先对齐 PRD 的任务路径与状态表达，再调视觉细节。

## ANTI-PATTERNS

- 不要把学生页做成教师管理页那种高密度控制台。
- 不要把主要操作埋进次级区域或过多弹窗。
- 不要因为追求“质感”让提交流程变绕。
- 不要弱化截止时间、提交状态、得分/反馈这些核心信息。
