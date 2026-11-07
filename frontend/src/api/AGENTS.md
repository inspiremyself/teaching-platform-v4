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

## CONVENTIONS

- 所有请求默认走 `request.ts`，共享 baseURL、token 注入、响应解包。
- 后端标准响应是 `ApiResponse<T>`；前端拿到的是 `res.data` 解包后的真实数据。
- 分页列表统一沿用 `PagedResult<T>`（与后端 `common/api/PagedResult.java` 同名）。
- 新增或重构接口时，优先补类型文件，减少 `any` 扩散。
- 如果某模块已有独立 API 文件，不要继续往 `teacher.ts` 里无差别堆聚合函数。

## ANTI-PATTERNS

- 不要在页面里直接 `axios.get('/api/v1/...')`。
- 不要在这里复制响应结构判断逻辑；统一留在 `request.ts`。
- 不要在新契约里继续扩大 `any` 的覆盖面。
- 不要让教师端路径和学生端路径语义混乱，例如把 `/teacher/...`、`/student/...`、通用 `/exams` 混成无规则集合而不写清用途。
