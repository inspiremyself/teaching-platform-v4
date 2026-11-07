# frontend api knowledge

## OVERVIEW

这里是前端 API 契约入口层；页面应通过这里访问后端，而不是各自拼 URL。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 统一请求/解包 | `../utils/request.ts` | `/api/v1`、401 处理、统一错误提示 |
| 认证 | `auth.ts` | 登录、当前用户、改密 |
| 教师聚合接口 | `teacher.ts` | 旧式聚合入口，仍被多页使用 |
| 学生聚合接口 | `student.ts` | 学生侧聚合入口之一 |
| 班级/学生 | `classes.ts` `admin.ts` | 班级列表与校管组织 API |
| 领域接口 | `materials.ts` `questions.ts` `labs.ts` `homeworks.ts` `exams.ts` | 模块化契约 |

## 规范引用

本目录的**契约细则**见 `.cursor/rules/frontend-api-contract.mdc`（URL 单一来源、解包、显式泛型、分页映射、类型与枚举单一来源、错误码两组常量）；页面调用纪律见 `.cursor/rules/frontend-code-style.mdc`；总纲见 `.cursor/rules/code-conventions.mdc`。本文件只保留目录导航与事实，不复制条款。

## CONVENTIONS

- 所有请求默认走 `request.ts`，共享 baseURL、token 注入、响应解包；拦截器成功时返回裸 `data`，页面不再判断 `res.code`。
- 后端标准响应是 `ApiResponse<T>`（字段 `code` / `message` / `data` / `timestamp`）；`code !== 0` 统一抛 `ApiError`。
- 后端 `PagedResult<T>` **在 `api/` 层映射**为数组或 `{ list, total }`，不直接透传给页面（旧口径「分页列表统一沿用 `PagedResult<T>`」已废止，见 `frontend-api-contract.mdc` §4）。
- 新增或重构接口时，优先补 `types/` 下的类型文件，减少 `any` 扩散；枚举取值来自 `types/enums.ts`。
- 如果某模块已有独立 API 文件，不要继续往 `teacher.ts` 里无差别堆聚合函数。
- `/api/v1` 字面量只出现在 `utils/request.ts`；完整 URL 由 `api/endpoints.ts` 的构造器生成。

## ANTI-PATTERNS

- 不要在页面里直接 `axios.get('/api/v1/...')`。
- 不要在这里复制响应结构判断逻辑；统一留在 `request.ts`。
- 不要在新契约里继续扩大 `any` 的覆盖面。
- 不要让教师端路径和学生端路径语义混乱，例如把 `/teacher/...`、`/student/...`、通用 `/exams` 混成无规则集合而不写清用途。
