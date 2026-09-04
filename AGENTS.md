# teaching-platform 项目知识库（注意时效性不一定最新）

**生成时间**：2026-04-13  
**分支**：`master`  
**基线提交**：`db16d84`

## OVERVIEW

## STRUCTURE

```text
teaching-platform/
├─ frontend/                 # Vue 3 + Vite + Pinia + Vue Router
│  └─ src/
│     ├─ api/                # 前端 API 契约入口
│     ├─ views/teacher/      # 教师端页面模式
│     ├─ views/student/      # 学生端页面模式
│     ├─ stores/             # 登录态/角色态中心
│     └─ utils/request.ts    # Axios 封装与统一响应解包
├─ backend/                  # Spring Boot + Security + JPA + Flyway
│  ├─ src/main/java/com/opencode/teachingplatform/
│  │  ├─ auth/               # JWT / 当前用户 / 登录与安全
│  │  ├─ common/             # 统一响应、异常、枚举、文件、审计
│  │  ├─ lab|homework|exam/  # 三条最复杂业务闭环
│  │  └─ ...                 # clazz/student/material/question/plagiarism/analysis
│  ├─ src/main/resources/    # application*.yml + db/migration
│  └─ src/test/              # 按业务包镜像组织的测试
├─ design/                   # 原型索引、设计系统、实现映射、证据说明
├─ design-prototypes/        # 本阶段核心原型资产输入（HTML/PNG/manifest）
├─ docker-compose.yml        # 本地整站联调入口（路径 A）
├─ data/                     # 路径 B 本地持久化（gitignore）：h2/、files/
├─ scripts/                  # 运维脚本：path-a / path-b / common
├─ docs/运维文档/            # 路径 A/B 启动与探活说明（日期前缀）
└─ README.md                 # 运行方式、默认账号、环境变量
```

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 前端启动链 | `frontend/src/main.ts` | 挂载 Pinia、Router、Element Plus |
| 路由与角色守卫 | `frontend/src/router/index.ts` | 教师/学生壳层、登录跳转、鉴权入口 |
| 登录态 | `frontend/src/stores/auth.ts` | token、user、homePath |
| 前端统一请求 | `frontend/src/utils/request.ts` | `/api/v1`、响应解包、401 处理 |
| 教师端页面 | `frontend/src/views/teacher/` | 管理型、高密度、工作台/列表/评分 |
| 学生端页面 | `frontend/src/views/student/` | 任务型、截止时间、作答/反馈 |
| 前端接口契约 | `frontend/src/api/` | 真实 API 调用入口，不要绕过 |
| 后端应用入口 | `backend/src/main/java/com/opencode/teachingplatform/TeachingPlatformApplication.java` | Spring Boot 启动类 |
| 安全入口 | `backend/src/main/java/com/opencode/teachingplatform/auth/security/SecurityConfig.java` | JWT + 角色访问控制 |
| 统一响应/异常 | `backend/src/main/java/com/opencode/teachingplatform/common/api/` `common/exception/` | `ApiResponse`、`PagedResult`、错误码 |
| 数据库迁移 | `backend/src/main/resources/db/migration/` | schema + seed |
| 测试根 | `backend/src/test/java/com/opencode/teachingplatform/` | controller/service 测试镜像 |
| 原型资产清单 | `design-prototypes/manifest.csv` | 页面标题与 HTML/PNG 资产总表 |
| 原型到实现映射 | `design/frontend-implementation-guide.md` | 页面模式、角色差异、落地优先级 |
| 本地启动（路径 A/B） | `docs/运维文档/`、`scripts/` | Compose / H2 文件库联调、探活脚本 |
| 路径 B H2 配置 | `backend/src/main/resources/application-test.yml` | 文件库仓库根 `data/h2/`（`H2_FILE`）；UT 仍用 test/resources mem |

## CODE MAP

| 符号/文件 | 角色 |
|---|---|
| `frontend/src/main.ts` | 前端 bootstrap |
| `frontend/src/router/index.ts` | 路由总表 + `beforeEach` 守卫 |
| `frontend/src/stores/auth.ts` | 登录态/角色主页中心 |
| `frontend/src/utils/request.ts` | Axios 实例、统一错误反馈 |
| `backend/.../TeachingPlatformApplication.java` | 后端启动入口 |
| `backend/.../auth/controller/AuthController.java` | 登录/当前用户/改密 API |
| `backend/.../exam/controller/ExamController.java` | 教师与学生考试流共用入口 |
| `backend/.../homework/controller/HomeworkController.java` | 教师发布/批改与学生提交流入口 |
| `backend/.../lab/controller/LabController.java` | 实验配置、作答、批改总入口 |
| `backend/.../common/api/ApiResponse.java` | 标准响应结构 |
| `backend/.../common/exception/GlobalExceptionHandler.java` | 错误码 → HTTP 状态映射 |

## CONVENTIONS

- 当前阶段优先级：**前端原型 / 产品交互目标 > 旧规范书细节 > 旧实现形态**。
- 仍必须保持真实系统约束：真实前后端、真实数据库、真实权限、真实测试，不允许 mock 页面、假 API、假数据闭环。
- 调试与 E2E 联调优先使用 **H2 文件库 + `test` profile + Flyway 种子数据**（数据目录仓库根 `data/h2/`，上传 `data/files/`，重启保留）；这套组合目前已验证能稳定支撑教师 `t9001/123456`、学生 `20260001/123456` 的真实链路测试。
- 若需要并行跑多套前后端实例，后端优先改 `SERVER_PORT`，前端通过 `VITE_API_TARGET` 定向代理到该后端，不要硬绑定唯一的 `8080/5173` 组合。
- 接口统一前缀仍是 `/api/v1`；统一响应仍是 `code`、`message`、`data`、`timestamp`。
- 前端统一通过 `frontend/src/utils/request.ts` 访问后端；不要在页面里直接手写 Axios 实例。
- 前端页面与原型对齐时，先保信息架构/任务路径，再追求像素复刻。
- 教师端优先高密度管理台模式；学生端优先任务导向模式。不要把两种信息节奏混成同一套模板。
- 后端当前常见模式是 `CurrentUser + service`；角色控制主要由 `@PreAuthorize` 和安全上下文完成。
- 测试当前以业务包镜像分布为主，常见拆法是 `controller tests + service tests`。
- `design-prototypes/` 是当前阶段核心输入目录，不是可随意忽略的附件目录。

## ANTI-PATTERNS

- 不要再把目标理解为“只改 CSS / 只做视觉美化”。
- 不要把现有前端页面结构当成不可动基线；它现在只是业务语义参考实现。
- 不要绕开原型资产直接拍脑袋改页面信息架构。
- 不要在前端散落响应结构假设；统一按 `request.ts` 的解包方式处理。
- 不要新增假接口、静态假图表、伪查重结果、占位工作流。
- 不要把状态值散落为硬编码字符串；后端状态以枚举为准，前端类型要跟进契约。
- 不要修改默认演示账号：教师 `t9001/123456`、学生 `20260001/123456`、班级 `SE2026-1`。

## UNIQUE STYLES

- 这个仓库同时存在“旧阶段规范导向”和“新阶段原型导向”两套文档语境；实现时必须显式偏向后者。
- `frontend/src/views/teacher/` 下既有旧平铺页面，也有已开始重构的新页面；改动前先判断当前文件属于哪一类。
- `design/` 更像解释层，`design-prototypes/` 更像证据层；两者用途不同，不要混写。

## COMMANDS

```bash
# 路径 A（Docker Compose）
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh

# 路径 B（本地 H2 文件库，推荐联调；两个终端）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh

# backend（仓库根目录先 cd backend；裸跑默认 local=MySQL）
./mvnw test
SPRING_PROFILES_ACTIVE=test ./mvnw spring-boot:run

# frontend
cd frontend && npm install && npm run dev && npm run build
```

运维细节：`docs/运维文档/2026-09-04-路径A-Docker-Compose整站启动.md`、`docs/运维文档/2026-09-04-路径B-本地开发H2启动.md`。

## NOTES

- 旧阶段原本只留下这一份根 `AGENTS.md`；本次已将其更新为新口径，并从这里开始向下分层。
- Java LSP 在当前环境不可用，后端结构判断主要依赖目录边界、控制器/测试组织与配置文件。
- `securityConfig` 仍使用 `NoOpPasswordEncoder`；这是现状事实，不代表后续重构方向。
