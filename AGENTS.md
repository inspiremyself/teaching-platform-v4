# teaching-platform 项目知识库

**生成时间**：2026-11-06  
**分支**：`main`  
**基线提交**：`543b3bb`

## OVERVIEW

高校教学一体化平台，覆盖 **实验、作业、考试** 三条核心教学闭环，并配套题库、课程资料、查重、成绩分析与学校组织管理。

- 三角色：`SCHOOL_ADMIN`（学校管理员）/ `TEACHER`（教师）/ `STUDENT`（学生），前端三套壳层、后端 JWT + `@PreAuthorize`。
- 前后端分离：Vue 3 + TypeScript + Element Plus；Spring Boot 3.3 + Spring Security + JPA + Flyway。
- 共享能力层：`assessment/`（题目快照、答案标准化、评分适配）+ `grading/`（策略模式评分引擎），被实验/作业/考试复用。
- 真实系统约束：真实前后端、真实数据库、真实权限、真实测试；禁止 mock 页面、假接口、假数据闭环。

## STRUCTURE

```text
teaching-platform-v4/
├─ frontend/                       # Vue 3.5 + Vite 5 + Pinia 3 + Vue Router 4
│  └─ src/
│     ├─ api/                      # Typed API 模块（子级 AGENTS）
│     ├─ components/               # lab/、question-config/ 共享组件
│     ├─ layout/                   # 三角色共用壳层
│     ├─ router/index.ts           # /teacher、/student、/admin 三套路由
│     ├─ stores/auth.ts            # token、user、角色主页
│     ├─ types/                    # 前端契约类型
│     ├─ utils/request.ts          # Axios 封装与统一响应解包
│     └─ views/
│        ├─ admin/                 # 校管端：学校/教师/学科/班级/学生/审计日志
│        ├─ teacher/               # 教师端：管理型高密度页面（子级 AGENTS）
│        ├─ student/               # 学生端：任务导向页面（子级 AGENTS）
│        ├─ auth/                  # 登录
│        └─ common/                # 个人中心
├─ backend/                        # Spring Boot 3.3 + Security + JPA + Flyway
│  ├─ src/main/java/com/opencode/teachingplatform/
│  │  ├─ admin/                    # 校管端 API（学校/教师/学科/班级/学生/审计日志）
│  │  ├─ analysis/                 # 成绩分析
│  │  ├─ assessment/               # 共享题目能力层（快照、适配器）
│  │  ├─ auth/                     # JWT / CurrentUser / 登录与安全
│  │  ├─ clazz/                    # 班级
│  │  ├─ common/                   # ApiResponse、异常、枚举、文件、审计、访问日志
│  │  ├─ exam/                     # 考试闭环
│  │  ├─ grading/                  # 共享评分引擎（Strategy Pattern）
│  │  ├─ homework/                 # 作业闭环
│  │  ├─ lab/                      # 实验闭环（含 behavior 埋点）
│  │  ├─ material/                 # 课程资料
│  │  ├─ plagiarism/               # 查重
│  │  ├─ question/                 # 题库
│  │  ├─ school/                   # 学校/学科实体与仓储
│  │  └─ student/                  # 学生
│  ├─ src/main/resources/          # application*.yml + db/migration
│  └─ src/test/                    # 按业务包镜像组织的测试（子级 AGENTS）
├─ docs/
│  ├─ PRD/                         # 需求文档（日期前缀）
│  ├─ 技术方案/                    # 技术方案（日期前缀）
│  ├─ 运维文档/                    # 路径 A/B/C 启动、备份、迁移
│  └─ superpowers/                 # plans / specs
├─ (当前阶段无原型资产：需求输入见 docs/PRD/ 与 docs/技术方案/)
├─ scripts/                        # path-a / path-b / path-c / common
├─ docker-compose.yml              # 路径 A：MySQL + 后端 + 前端
├─ data/                           # 本地持久化（gitignore）：h2/、files/、logs/
├─ logs/                           # 历史运行日志（gitignore）
└─ README.md                       # 运行方式、演示账号、技术栈
```

分层知识库：`backend/AGENTS.md`、`frontend/AGENTS.md`，以及 `frontend/src/api/`、`frontend/src/views/teacher/`、`frontend/src/views/student/`、`frontend/src/views/admin/`、`backend/src/main/java/.../`、`backend/src/test/java/.../` 下的子级 `AGENTS.md`（共 8 个子级）。涉及具体层时继续向下读。`docs/` 与 `scripts/` 不设子级知识库，分别由 `docs/运维文档/README.md` 与 `.cursor/rules/ops-docs-and-scripts.mdc` 承担索引职责。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 产品需求 | `docs/PRD/` | 当前阶段需求输入，文件名带日期前缀 |
| 技术方案 | `docs/技术方案/` | 已定稿方案；实现前先读对应方案 |
| 前端启动链 | `frontend/src/main.ts` | 挂载 Pinia、Router、Element Plus |
| 路由与角色守卫 | `frontend/src/router/index.ts` | 三套路由树：`/teacher`、`/student`、`/admin` |
| 登录态 | `frontend/src/stores/auth.ts` | token、user、homePath |
| 前端统一请求 | `frontend/src/utils/request.ts` | `/api/v1`、响应解包、401 处理 |
| 前端接口契约 | `frontend/src/api/` | 真实 API 调用入口，不要绕过 |
| 前端契约类型 | `frontend/src/types/` | 与后端 DTO/枚举同步 |
| 教师端页面 | `frontend/src/views/teacher/` | 管理型、高密度；考试页在 `exams/` |
| 学生端页面 | `frontend/src/views/student/` | 任务型；考试页在 `exams/` |
| 校管端页面 | `frontend/src/views/admin/` | 学校、教师、学科、班级、学生、审计日志 |
| 共享题目组件 | `frontend/src/components/question-config/` | 题目编辑器、题目选择器、配置列表 |
| 后端应用入口 | `backend/src/main/java/com/opencode/teachingplatform/TeachingPlatformApplication.java` | Spring Boot 启动类 |
| 安全入口 | `backend/.../auth/security/SecurityConfig.java` | JWT + 角色访问控制 |
| 共享题目/评分层 | `backend/.../assessment/`、`backend/.../grading/` | 快照、适配器、评分策略 |
| 统一响应/异常 | `backend/.../common/api/`、`common/exception/` | `ApiResponse`、`PagedResult`、错误码 |
| 行为埋点 | `backend/.../lab/behavior/` | 实验作答事件采集与风险指标 |
| 审计/访问日志 | `backend/.../common/audit/`、`common/accesslog/` | 操作日志与 HTTP 访问日志 |
| 数据库迁移 | `backend/src/main/resources/db/migration/` | 当前 `V1`–`V24`（24 个），schema + seed |
| 测试根 | `backend/src/test/java/com/opencode/teachingplatform/` | controller/service 测试镜像（61 个 `*Tests.java`） |
| 设计资产状态 | `docs/PRD/`、`docs/技术方案/` | 当前阶段无原型资产，需求输入以 PRD 与技术方案为准 |
| 本地启动（路径 A/B/C） | `docs/运维文档/`、`scripts/` | Compose / 本机 MySQL / H2 文件库 |
| 本地启动规则 | `.cursor/rules/local-dev-startup.mdc` | 端口、代理、数据目录、探活 |

## CODE MAP

| 符号/文件 | 角色 |
|---|---|
| `frontend/src/main.ts` | 前端 bootstrap |
| `frontend/src/router/index.ts` | 路由总表 + `beforeEach` 守卫（三角色） |
| `frontend/src/stores/auth.ts` | 登录态/角色主页中心 |
| `frontend/src/utils/request.ts` | Axios 实例、统一错误反馈 |
| `frontend/src/api/exams.ts` | 教师/学生考试 API 契约 |
| `frontend/src/api/questions.ts` | 题库 API 契约 |
| `frontend/src/components/question-config/QuestionEditorDialog.vue` | 题目编辑弹窗 |
| `frontend/src/components/question-config/QuestionPickerDialog.vue` | 从题库选题 |
| `frontend/src/components/question-config/QuestionConfigList.vue` | 题目配置列表 |
| `backend/.../TeachingPlatformApplication.java` | 后端启动入口 |
| `backend/.../auth/controller/AuthController.java` | 登录/当前用户/改密 API |
| `backend/.../exam/controller/ExamController.java` | 教师与学生考试流共用入口 |
| `backend/.../homework/controller/HomeworkController.java` | 教师发布/批改与学生提交入口 |
| `backend/.../lab/controller/LabController.java` | 实验配置、作答、批改总入口 |
| `backend/.../question/controller/QuestionController.java` | 题库 CRUD |
| `backend/.../admin/controller/AdminSchoolController.java` | 校管端学校/组织入口 |
| `backend/.../analysis/controller/AnalysisController.java` | 成绩分析入口 |
| `backend/.../assessment/service/QuestionSnapshotFactory.java` | 题目快照构造 |
| `backend/.../grading/service/ScoringEngine.java` | 策略评分入口 |
| `backend/.../common/api/ApiResponse.java` | 标准响应结构 |
| `backend/.../common/exception/GlobalExceptionHandler.java` | 错误码 → HTTP 状态映射 |

## CONVENTIONS

- 当前阶段优先级：**PRD / 原型交互目标 > 旧规范书细节 > 旧实现形态**。
- 仍必须保持真实系统约束：真实前后端、真实数据库、真实权限、真实测试，不允许 mock 页面、假 API、假数据闭环。
- 三角色边界固定：教师 `TEACHER`、学生 `STUDENT`、校管 `SCHOOL_ADMIN`；前端三套壳层、后端 `@PreAuthorize` 双重约束，不要把角色语义合并。
- 调试与 E2E 联调**默认** `./scripts/path-b/backend.sh`（本机 MySQL，`local` profile）。无 MySQL 时用 `./scripts/path-b/backend-h2.sh`（H2 文件库 + `test` profile）。
- `application.yml`：`ddl-auto=validate`、`flyway.enabled=true`；`application-test.yml`（H2 文件库）：`ddl-auto=none`，仍由 Flyway 建表。测试资源 `src/test/resources/application-test.yml` 使用 H2 mem，与路径 B 隔离。
- 数据库结构变更一律新增 migration，不改已应用的历史脚本；当前 `V1`–`V20`。`V15`、`V16`、`V18` 使用双方言占位符（`alterModifyColumn` / `fkChecksDisable` / `fkChecksEnable` / `mysqlOnly`），`application.yml` 与 `application-test.yml` 必须分别提供 MySQL / H2 值。
- **本地业务数据**不在 Flyway 种子内；清库即丢。破坏性操作前须 `./scripts/common/backup-local-data.sh`；H2 清库须 `./scripts/path-b/reset-h2.sh`（见 `path-b-data-backup-gate.mdc`）。
- `data/` 整树禁止随意删除（见 `protect-data-directory.mdc`）：`data/h2/`（H2 持久化）、`data/files/`（上传）、`data/logs/`（一键启动日志）。
- 若需要并行跑多套前后端实例，后端优先改 `SERVER_PORT`，前端通过 `VITE_API_TARGET` 定向代理到该后端，不要硬绑定唯一的 `8080/5173` 组合。
- 接口统一前缀仍是 `/api/v1`；统一响应仍是 `code`、`message`、`data`、`timestamp`；分页统一 `PagedResult<T>`。
- 前端统一通过 `frontend/src/utils/request.ts` 访问后端；不要在页面里直接手写 Axios 实例；类型放 `frontend/src/types/`。
- 前端页面与原型对齐时，先保信息架构/任务路径，再追求像素复刻。
- 教师端优先高密度管理台模式；学生端优先任务导向模式；校管端优先组织与治理视角。不要把三种信息节奏混成同一套模板。
- 后端当前常见模式是 `CurrentUser + service`；角色控制主要由 `@PreAuthorize` 和安全上下文完成；controller 只做参数与转发。
- 测试当前以业务包镜像分布为主（当前 61 个 `*Tests.java`），常见拆法是 `controller tests + service tests`；controller 测试多为 `@WebMvcTest` + `MockMvc` + `@AutoConfigureMockMvc(addFilters = false)`。
- 新增功能先读 `docs/PRD/` 对应需求，再按 `.cursor/rules/feature-dev-workflow.mdc` 与 `tech-spec-decisive.mdc` 输出技术方案并完成自查；方案未定稿不进入编码。
- 当前阶段无原型资产；需求输入以 `docs/PRD/` 与 `docs/技术方案/` 为准，不引用不存在的 `design/`、`design-prototypes/` 目录作为依据。

## ANTI-PATTERNS

- 不要再把目标理解为“只改 CSS / 只做视觉美化”。
- 不要把现有前端页面结构当成不可动基线；它现在只是业务语义参考实现。
- 不要绕开 `docs/PRD/` 与 `docs/技术方案/` 直接拍脑袋改页面信息架构。
- 不要在前端散落响应结构假设；统一按 `request.ts` 的解包方式处理。
- 不要新增假接口、静态假图表、伪查重结果、占位工作流。
- 不要把状态值散落为硬编码字符串；后端状态以枚举为准，前端类型要跟进契约。
- 不要直接修改运行中 schema、手工补表，或修改已应用的 Flyway 脚本；一律新增 migration。
- 不要删除 `data/` 或 `logs/`；清 H2 只能走 `reset-h2.sh`，MySQL 破坏性操作前先备份。`target/`、`dist/`、`node_modules/` 是构建产物，不要当作源码依据。
- 不要让教师/学生/校管三套路由与权限混成单套壳层。
- 不要绕过 `frontend/src/api/` 与 `request.ts` 自行发请求。
- 不要修改默认演示账号：校管 `admin9001/123456`、教师 `t9001/123456`、学生 `20260001/123456`；演示班级 `SE2026-1`。
- 不要把 `README.md` 中的功能数量、迁移数量当作当前事实；以代码、`db/migration/` 与 `package.json` / `pom.xml` 为准。

## UNIQUE STYLES

- 这个仓库同时存在“旧阶段规范导向”和“新阶段 PRD/技术方案导向”两套文档语境；实现时必须显式偏向后者。
- `frontend/src/views/teacher/` 下既有旧平铺页面，也有已开始重构的新页面；改动前先判断当前文件属于哪一类。
- 当前阶段无原型资产；旧阶段提到的解释层与证据层目录均不存在，需求与方案以 `docs/PRD/`、`docs/技术方案/` 为准。
- 知识库是分层结构：根 `AGENTS.md` 只保留跨层事实，具体 API、教师页、学生页、校管页、后端域、测试约定继续下沉到子级 `AGENTS.md`。

## COMMANDS

```bash
# 路径 A（Docker Compose：MySQL + 后端 + 前端）
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh

# 路径 B（本机 MySQL，两个终端）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh

# 路径 B 一键后台启动 / 停止 / 状态 / 日志（默认 MySQL）
./scripts/path-b/dev.sh up
./scripts/path-b/dev.sh status
./scripts/path-b/dev.sh logs backend
./scripts/path-b/dev.sh down
DB=h2 ./scripts/path-b/dev.sh up        # 后端改用 H2 文件库

# H2 文件库（可选，两个终端）
./scripts/path-b/backend-h2.sh
./scripts/path-b/frontend.sh

# 路径 C（等价 path-b/backend.sh，保留文档引用）
./scripts/path-c/backend.sh

# backend 测试（仓库根目录先 cd backend）
./mvnw test
./mvnw -Dtest=ExamServiceTests test

# H2 → MySQL 整库迁移（须先停后端）
./scripts/common/migrate-h2-to-mysql.sh

# 数据备份（MySQL 默认）
./scripts/common/backup-local-data.sh --label milestone --with-files
./scripts/common/verify-local-backup.sh --latest
./scripts/common/restore-local-data.sh --latest --with-files   # 须先停后端

# frontend（build 含 vue-tsc --noEmit）
cd frontend && npm install && npm run dev && npm run build
```

端口与代理：默认后端 `8080`、前端 `5173`；换后端端口用 `SERVER_PORT=18080`，前端用 `VITE_API_TARGET=http://127.0.0.1:18080`。

运维细节：`docs/运维文档/2026-09-04-路径A-Docker-Compose整站启动.md`、`docs/运维文档/2026-09-09-本地数据备份与恢复.md`、`docs/运维文档/2026-09-09-路径C-本地MySQL联调与H2迁移.md`、`docs/运维文档/2026-10-08-路径B-一键启动前后端dev脚本.md`。

## NOTES

- 旧阶段原本只留下一份根 `AGENTS.md`；现已更新为 2026-11-06 口径，并向 `backend/`、`frontend/` 及其子目录分层。
- Java LSP 在当前环境不可用；后端结构判断主要依赖目录边界、控制器/测试组织与配置文件。
- `SecurityConfig` 使用 `PasswordEncoderFactories.createDelegatingPasswordEncoder()`（新写入密码为 `{bcrypt}`），旧的无操作密码编码器已弃用。
- 根目录 `ppt-source-pack/`、`tmp-qa-material.txt` 是阶段性材料，不作为代码结构或运行依据。

## 事实核对清单

文档变更时逐项与仓库实测核对，避免再次漂移：

| 项 | 当前事实 |
|---|---|
| 后端测试数 | 61 个 `*Tests.java`（`backend/src/test/java/`） |
| 迁移脚本 | `V1`–`V24` 共 24 个（`backend/src/main/resources/db/migration/`） |
| 角色数 | 3 个：`TEACHER`、`STUDENT`、`SCHOOL_ADMIN` |
| 密码编码器 | `DelegatingPasswordEncoder` |
| 分页类型 | `PagedResult<T>`（`backend/.../common/api/PagedResult.java`） |
| 设计资产状态 | 当前阶段无原型资产；需求输入为 `docs/PRD/` 与 `docs/技术方案/` |
