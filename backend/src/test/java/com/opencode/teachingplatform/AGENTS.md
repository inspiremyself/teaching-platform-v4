# backend test knowledge

## OVERVIEW

测试目录按业务包镜像组织，普遍分成 controller tests 与 service tests 两类，覆盖真实角色与业务闭环。

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 登录/鉴权测试 | `auth/` | 登录、当前用户、角色错误 |
| 班级/学生/学校测试 | `clazz/` `student/` `school/` | 班级分页、班级成员查询、学校隔离查询 |
| 实验/作业/考试测试 | `lab/` `homework/` `exam/` | 闭环主链路 |
| 资料/题库/查重/统计测试 | `material/` `question/` `plagiarism/` `analysis/` | 辅助域与聚合域 |
| 测试配置 | `../resources/application-test.yml` | H2 + Flyway |

## 规范引用

测试命名、三类注解矩阵、公共 fixture、CI 命令见 `.cursor/rules/toolchain-and-test-conventions.mdc` §4；UT 与路径 B 的 profile 隔离见 `.cursor/rules/backend-db-and-config.mdc` §3；演示账号见 `.cursor/rules/local-dev-startup.mdc`；总纲见 `.cursor/rules/code-conventions.mdc`。本文件只保留断言纪律，不复制条款。

## CONVENTIONS

- 三类注解并存，按被测对象选，不要一律照抄 `@WebMvcTest`：Controller 切片用 `@WebMvcTest` + `MockMvc` + `@AutoConfigureMockMvc(addFilters = false)`；admin 集成用 `@SpringBootTest` + `@AutoConfigureMockMvc`；Service / 持久化用 `@DataJpaTest` + `@Import` 或纯 Mockito。矩阵见 `toolchain-and-test-conventions.mdc` §4。
- 安全相关依赖常通过 `@MockBean JwtAuthenticationFilter` 或 token service 隔离。
- 断言要覆盖 `code`、`message`、`timestamp` 与关键 `data` 字段，不只看 HTTP 状态；断言库用 AssertJ / MockMvc `jsonPath`。
- 演示账号与班级种子值经常作为测试事实出现：`t9001`、`20260001`、`SE2026-1`（见 `local-dev-startup.mdc`，不得改）。
- 测试环境当前走 H2 mem + Flyway；新增迁移时要考虑测试是否仍可启动。

## ANTI-PATTERNS

- 不要把 controller 测试退化成只断言 `status().isOk()`。
- 不要跳过角色/越权场景；这里已有较强权限测试惯例。
- 不要为了测试通过去删闭环断言或改演示基线事实。
