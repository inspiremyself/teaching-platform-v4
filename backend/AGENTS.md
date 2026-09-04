# backend workspace knowledge

## OVERVIEW

这里是后端运行与配置边界，不只是 `src/main/java` 代码树；启动、环境切换、Flyway、测试 profile、容器化都从这一层进入。

## STRUCTURE

```text
backend/
├─ pom.xml                    # Spring Boot 依赖与测试依赖
├─ mvnw / mvnw.cmd            # 本地 Maven Wrapper 入口
├─ Dockerfile                 # 后端容器构建入口
├─ src/main/java/...          # 业务代码；其下已有更细 AGENTS
├─ src/main/resources/        # application*.yml + db/migration
├─ src/test/java/...          # 测试代码；其下已有测试 AGENTS
└─ src/test/resources/        # 测试 profile 与测试资源
```

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 后端依赖/插件 | `pom.xml` | Spring Boot 3.3、JPA、Security、Flyway、H2 |
| 本地启动 | `mvnw` `mvnw.cmd` | Windows 与类 Unix 都有 wrapper |
| 主配置 | `src/main/resources/application.yml` | profile、数据源、JWT、文件存储 |
| 本地 profile | `src/main/resources/application-local.yml` | 本地运行覆盖项 |
| 测试 profile | `src/main/resources/application-test.yml` | 路径 B 运行：H2 **文件库** 仓库根 `data/h2/`（`H2_FILE`）+ Flyway |
| 测试 profile（UT） | `src/test/resources/application-test.yml` | 单元/集成测试：H2 **mem**，与路径 B 隔离 |
| 数据库迁移 | `src/main/resources/db/migration/` | schema/seed 入口 |
| 业务代码 | `src/main/java/com/opencode/teachingplatform/` | 其下按业务域分包 |
| 测试代码 | `src/test/java/com/opencode/teachingplatform/` | 其下按业务包镜像分布 |

## CONVENTIONS

- 启动与验证默认优先用 wrapper，不假设全局 Maven 已安装。
- 数据库结构变更一律通过 `db/migration/` 演进；`ddl-auto=validate` 说明运行时不应依赖自动建表。
- `application.yml` 是主配置入口；profile 只做环境差异，不要把核心约束散落到多个 profile 难以追踪。
- 路径 B（`main` 的 `test` profile）使用 H2 文件库持久化；单元测试资源保持 mem。二者有意不同，改迁移时两边都要能过。
- **调试、联调、Playwright、演示链路优先跑 `test` profile（H2 文件库）**。当前机器上默认 `local` profile 常会因 MySQL 不可达而启动失败，继而让前端登录接口表现成 500。
- Windows 下推荐启动方式：`cmd /c "set SPRING_PROFILES_ACTIVE=test && mvnw.cmd spring-boot:run"`。
- 若默认端口冲突，推荐：`cmd /c "set SPRING_PROFILES_ACTIVE=test && set SERVER_PORT=18080 && mvnw.cmd spring-boot:run"`。
- 开始前端或 Playwright 联调前，先直连探活一次 `POST /api/v1/auth/login`，确认 H2 后端真的已起来，再开始浏览器测试。
- `src/main/java/...` 与 `src/test/java/...` 已各自有子级 AGENTS；涉及具体业务代码或测试写法时应继续向下读子规则。

## ANTI-PATTERNS

- 不要直接修改运行中 schema 或手工补表而不写 migration。
- 不要只改 `src/test/resources/application-test.yml` 而忽略主资源里的 test profile；新增迁移时两边都要验证。
- 不要把路径 B 的文件库改回 mem，除非明确要放弃持久化；也不要把 UT 的 mem 改成文件库。
- 不要把运行日志、`target/`、`data/` 中的产物当作源码依据。
- 不要绕过 `mvnw` 另写临时启动说明，除非仓库命令面真的发生变化。
- 不要在 `8080` 端口状态不明时反复重启后端；先确认端口是否被旧进程占用，再决定换端口或清理进程。

## COMMANDS

```bash
# 推荐：仓库根目录脚本（路径 B = H2 文件库）
../scripts/path-b/backend.sh
# 或本目录：
SPRING_PROFILES_ACTIVE=test ./mvnw spring-boot:run
SERVER_PORT=18080 SPRING_PROFILES_ACTIVE=test ./mvnw spring-boot:run

# Windows
cmd /c "set SPRING_PROFILES_ACTIVE=test && mvnw.cmd spring-boot:run"
cmd /c "set SPRING_PROFILES_ACTIVE=test && set SERVER_PORT=18080 && mvnw.cmd spring-boot:run"

# 测试
./mvnw test
```

数据目录：仓库根 `data/h2/`（路径 B 持久化）、`data/files/`（上传）；清空 H2 后重启会重新 Flyway 灌种。  
运维文档：`docs/运维文档/2026-09-04-路径B-本地开发H2启动.md`。
