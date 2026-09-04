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

### 方式二：本地开发（路径 B，推荐日常联调）

H2 **文件库**（仓库根 `data/h2/`），无需 MySQL，**重启数据保留**。清空可删该目录后重启。上传文件在 `data/files/`（答题图：`data/files/lab-answers/{yyyy-MM-dd}/`）。

```bash
# 两个终端
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh
```

等价手工命令（仍建议用脚本，脚本会注入仓库根绝对路径）：

```bash
cd backend && \
  SPRING_PROFILES_ACTIVE=test \
  H2_FILE="$(pwd)/../data/h2/teaching_platform" \
  FILE_STORAGE_ROOT="$(pwd)/../data/files" \
  ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

### 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 教师 | t9001 | 123456 |
| 学生 | 20260001 | 123456 |

演示班级：`SE2026-1`  
登录须选择对应身份（教师 / 学生）。

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
