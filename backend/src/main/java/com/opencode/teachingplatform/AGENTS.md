# backend domain knowledge

## OVERVIEW

后端按业务域分包：controller / service / repository / entity / dto 的层次基本稳定，`auth`、`lab`、`homework`、`exam` 是最高复杂度区域。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 应用入口 | `TeachingPlatformApplication.java` | Spring Boot 启动类 |
| 认证安全 | `auth/` | JWT、CurrentUser、登录、改密 |
| 通用能力 | `common/` | `ApiResponse`、`PagedResult`、异常、枚举、文件、审计 |
| 实验闭环 | `lab/` | 教师配置、学生作答、评分、自动评分 |
| 作业闭环 | `homework/` | 教师发布/批改、学生提交、查重链路 |
| 考试闭环 | `exam/` | 创建、发布、开考、交卷、阅卷 |
| 资料/题库/班级/学生 | `material/` `question/` `clazz/` `student/` | 支撑型业务域 |
| 统计/查重 | `analysis/` `plagiarism/` | 聚合与独立工作流 |

## 规范引用

Java 类写法、注入、响应与错误码段位见 `.cursor/rules/backend-code-style.mdc`；五层分包、例外矩阵与依赖方向见 `.cursor/rules/backend-package-layout.mdc`；表结构 / Flyway / profile 见 `.cursor/rules/backend-db-and-config.mdc`；总纲见 `.cursor/rules/code-conventions.mdc`。本文件只保留业务域目录导航，不复制条款。

## CONVENTIONS

- 控制器统一走 `/api/v1`；角色访问通过 `@PreAuthorize` 明确声明（角色字符串白名单见 `backend-code-style.mdc` §3）。
- 控制器只取 `SecurityUtils.currentUser()` 并转发到 service；复杂业务不堆在 controller。
- 标准响应统一 `ApiResponse.ok(...)` / `ApiResponse.of(...)`；分页返回 `ApiResponse<PagedResult<?>>`。
- 错误码到 HTTP 状态的映射集中在 `common/exception/GlobalExceptionHandler.java`；段位与兜底见 `backend-code-style.mdc` §5。
- 表结构改动优先走 migration，`ddl-auto` 矩阵见 `backend-db-and-config.mdc` §3，不要偷改运行时 schema。
- 状态值优先放在 `common/enums/` 或领域枚举中，枚举落点按同域现状跟随（见 `backend-package-layout.mdc` §5），不散落魔法字符串。
- 审计 / 访问日志已归位 `common/infra/audit/`、`common/infra/accesslog/`（旧路径 `common/audit/`、`common/accesslog/` 已废止）。

## ANTI-PATTERNS

- 不要在 controller 里直接写复杂业务判断。
- 不要跳过安全上下文，手工传角色字符串做授权。
- 不要绕开 Flyway 手改数据库基线却不补 migration。
- 不要在新接口里破坏统一响应结构。
- 不要把实验、作业、考试三条链路混成“一个通用提交模块”；这里的状态机和角色动作不同。
