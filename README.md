# Teaching Platform v4

高校教学一体化平台——覆盖实验、作业、考试三大核心教学闭环的全栈管理系统。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5 + TypeScript 5.8 + Vite 5 + Pinia 3 + Vue Router 4 + Element Plus |
| 后端 | Spring Boot 3.3 + Spring Security + Spring Data JPA + Flyway |
| 数据库 | MySQL 8.2（生产）/ H2（测试） |
| 认证 | JWT（jjwt 0.12.6）+ 无状态会话 |
| 部署 | Docker Compose（MySQL + 后端 + 前端三容器） |

## 功能概览

### 教师端

- 仪表盘（KPI 概览）
- 班级管理、学生管理（批量导入）
- 课程资料上传/下载
- 题库管理（单选/多选/判断/填空/简答 5 种题型）
- 实验管理：步骤配置、分步答题、自动评分、教师批改、实验报告生成、批量批改
- 作业管理：出题、提交跟踪、逐题批改、查重检测
- 考试管理：组卷、定时开考、自动评分客观题、教师批改主观题
- 成绩分析仪表盘
- 查重任务管理

### 学生端

- 仪表盘（待办/截止日期）
- 课程资料查看/下载
- 实验：分步答题工作台（选择/填空/文本/代码编辑器）
- 作业：逐题作答与提交
- 考试：限时答题、自动交卷
- 成绩查看

## 快速开始

详细运维说明见 [`docs/运维文档/`](docs/运维文档/)。推荐优先用仓库脚本。

### 方式一：Docker Compose（路径 A）

```bash
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
```

启动后访问：
- 前端：http://localhost:5173
- 后端 API：http://localhost:8080/api/v1

停止：`./scripts/path-a/down.sh`（加 `--volumes` 会清空 MySQL / 上传文件卷）

### 方式二：本地开发（推荐日常联调，默认 MySQL）

本机 MySQL 库 `teaching_platform`（默认 `root` / `12345678`）。上传文件在 `data/files/`。  
无 MySQL 时可用 H2：`./scripts/path-b/backend-h2.sh`（见 [路径 B H2 文档](docs/运维文档/2026-09-04-路径B-本地开发H2启动.md)）。  
H2 数据目录备份/清库见 [本地数据备份与恢复](docs/运维文档/2026-09-09-本地数据备份与恢复.md)。

```bash
# 两个终端（默认 MySQL）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh
```

从 H2 迁入 MySQL（一次性）：`./scripts/common/migrate-h2-to-mysql.sh`

H2 联调（可选）：

```bash
./scripts/path-b/backend-h2.sh
./scripts/path-b/frontend.sh
```

### 方式三：本机 MySQL（路径 C，与方式二 backend 相同）

`./scripts/path-c/backend.sh` 与 `./scripts/path-b/backend.sh` 等价。详见 [`docs/运维文档/2026-09-09-路径C-本地MySQL联调与H2迁移.md`](docs/运维文档/2026-09-09-路径C-本地MySQL联调与H2迁移.md)。

### 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 学校管理员 | admin9001 | 123456 |
| 教师 | t9001 | 123456 |
| 教师 | t9002 | 123456 |
| 学生 | 20260001 | 123456 |

演示学校：`演示学校`  
演示班级：`SE2026-1`  
演示学科：`SE-ARCH`（软件体系结构）、`DB-PRINCIPLE`（数据库原理）  
登录须选择对应身份（学校管理员 / 教师 / 学生）。

可选校管探活：

```bash
USERNAME=admin9001 ROLE=SCHOOL_ADMIN ./scripts/common/healthcheck-login.sh
```

### 数据备份（MySQL 表结构 + 数据）

Flyway 迁移、覆盖库或重要里程碑前：

```bash
./scripts/common/backup-local-data.sh --label milestone-xaust --with-files
./scripts/common/verify-local-backup.sh --latest
```

恢复（须先停后端）：`./scripts/common/restore-local-data.sh --latest --with-files`  
详见 [本地数据备份与恢复](docs/运维文档/2026-09-09-本地数据备份与恢复.md)。

## 项目结构

```
teaching-platform-v4/
├── data/                             # 路径 B 本地数据（gitignore）：h2/、files/
├── backend/                          # Spring Boot 后端
│   └── src/main/java/com/opencode/teachingplatform/
│       ├── auth/                     # JWT 认证、安全配置
│       ├── common/                   # ApiResponse、全局异常处理、枚举、文件存储
│       ├── grading/                  # 共享评分引擎（Strategy Pattern）
│       │   ├── ScoringEngine.java
│       │   └── strategy/            # 单选/多选/判断/填空/简答评分策略
│       ├── assessment/              # 共享题目能力层（快照、适配器）
│       ├── lab/                     # 实验模块
│       ├── homework/                # 作业模块
│       ├── exam/                    # 考试模块
│       ├── question/                # 题库管理
│       ├── student/                 # 学生管理
│       ├── clazz/                   # 班级管理
│       ├── material/                # 课程资料
│       ├── plagiarism/              # 查重检测
│       └── analysis/                # 成绩分析
├── frontend/                         # Vue 3 前端
│   └── src/
│       ├── api/                     # Typed API 模块
│       ├── router/                  # 双壳路由（教师/学生）
│       ├── stores/                  # Pinia 状态管理
│       ├── views/teacher/           # 20+ 教师页面
│       ├── views/student/           # 10+ 学生页面
│       ├── components/              # 共享组件（题目配置等）
│       └── types/                   # TypeScript 类型定义
├── docker-compose.yml
└── AGENTS.md                         # AI Agent 知识库
```

## 架构设计要点

- **双角色隔离**：教师/学生完全独立的路由树、API 权限和数据访问，通过 JWT + `@PreAuthorize` 强制执行
- **策略模式评分引擎**：`grading/` 模块通过 `QuestionScoringStrategy` 接口支持 5 种题型，可扩展
- **共享题目能力层**：`assessment/` 模块通过 QuestionSnapshot + GradingAdapter 实现实验/作业/考试的评分复用
- **数据库版本化**：Flyway 管理 12 个迁移脚本，`ddl-auto=validate` 确保启动时 schema 一致性
- **评分与可见性解耦**：成绩生成独立于学生可见性，教师可控制成绩发布时机

## API 约定

- 基础路径：`/api/v1`
- 统一响应格式：`{ code, message, data, timestamp }`
- 认证：`Authorization: Bearer <JWT>`
- 权限：`@PreAuthorize("hasRole('TEACHER')")` / `hasRole('STUDENT')`
