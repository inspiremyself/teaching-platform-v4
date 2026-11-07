# admin views knowledge

## OVERVIEW

校管端面向学校组织与治理，角色固定为 `SCHOOL_ADMIN`，与教师端管理台、学生端任务页的节奏都不同：`/admin` 路由树由 `frontend/src/router/index.ts` 的 `meta.role` 守卫隔离。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 校管工作台 | `Dashboard.vue` | 组织概览入口 |
| 学校信息 | `School.vue` | 学校档案 |
| 教师管理 | `Teachers.vue` | 教师账号与任职 |
| 学科字典 | `Subjects.vue` | 学校学科维护 |
| 班级管理 | `Classes.vue` | 班级列表、状态、多教师指派 |
| 学生管理 | `Students.vue` | 学生账号、导入、重置密码 |
| 审计日志 | `AuditLogs.vue` | 操作日志查询 |

## CONVENTIONS

- 所有请求走 `frontend/src/api/admin.ts`，经 `utils/request.ts` 统一解包；页面不拼接 `/api/v1` 路径。
- 分页契约统一 `PagedResult<T>`；错误码与提示沿用 `request.ts` 的静默清单。
- 学校维度隔离由后端 `SCHOOL_ADMIN` 权限与 `schoolId` 保证，前端不自行拼接跨校查询参数。
- 组织变更属于破坏性操作场景，涉及导入/清库时按 `.cursor/rules/path-b-data-backup-gate.mdc` 先备份。

## ANTI-PATTERNS

- 不要把校管页并入教师端路由或复用教师端导航语义。
- 不要把班级/学生管理做成学生端任务卡片。
- 不要在页面里缓存学校列表作为跨请求的权限判断依据。
