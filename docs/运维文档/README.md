# 运维文档索引

本目录存放本地启动与运维说明；文件名以**创建日期**为前缀。

| 文档 | 说明 | 脚本 |
|------|------|------|
| [2026-09-04-路径A-Docker-Compose整站启动.md](./2026-09-04-路径A-Docker-Compose整站启动.md) | MySQL + 后端 + 前端三容器 | `scripts/path-a/` |
| [2026-09-04-路径B-本地开发H2启动.md](./2026-09-04-路径B-本地开发H2启动.md) | **可选** H2 文件库联调 | `scripts/path-b/backend-h2.sh` |
| [2026-09-07-路径B-添加教师账号.md](./2026-09-07-路径B-添加教师账号.md) | 路径 B 已启动时新增教师账号 | `scripts/common/create-teacher.sh` |
| [2026-09-09-本地数据备份与恢复.md](./2026-09-09-本地数据备份与恢复.md) | **默认 MySQL** 表结构+数据；可选 H2/files | `backup-local-data.sh`、`restore-local-data.sh`、`verify-local-backup.sh` |
| [2026-09-09-路径B-数据备份与恢复.md](./2026-09-09-路径B-数据备份与恢复.md) | H2 专用（历史索引） | `backup-path-b-data.sh`、`restore-path-b-data.sh`、`reset-h2.sh` |
| [2026-09-09-路径C-本地MySQL联调与H2迁移.md](./2026-09-09-路径C-本地MySQL联调与H2迁移.md) | 本机 MySQL 联调；H2 整库迁入 | `scripts/path-c/`、`migrate-h2-to-mysql.sh` |
| [2026-09-11-nvm多项目Node版本与IDE配合.md](./2026-09-11-nvm多项目Node版本与IDE配合.md) | nvm 多版本、`.nvmrc`、Cursor/VS Code 与终端对齐 | （本机 `~/.zshrc` / 项目 `.nvmrc`） |
| [2026-10-08-路径B-一键启动前后端dev脚本.md](./2026-10-08-路径B-一键启动前后端dev脚本.md) | **单终端一键后台启停前后端**（MySQL 或 H2） | `scripts/path-b/dev.sh` |
| [2026-11-12-AI网关配置注入与故障排查.md](./2026-11-12-AI网关配置注入与故障排查.md) | 「AI 生成」4 个环境变量注入、生效判定与故障排查（路径 A / B / C） | `scripts/path-b/dev.sh`、`scripts/common/healthcheck-login.sh` |
| [2026-11-12-frp-dev演示链路性能配置.md](./2026-11-12-frp-dev演示链路性能配置.md) | frp dev 演示链路首屏性能配置与验收 | `scripts/path-b/dev-public.sh` |

## MySQL 口令口径

`docker-compose.yml` 的 `MYSQL_ROOT_PASSWORD: 123456` 只作用于路径 A 容器内的 MySQL；`scripts/path-b/backend.sh` 的 `DB_PASS=12345678` 作用于路径 B 的本机 MySQL。两者各自生效、互不影响，脚本中的口令值不因另一方改动而调整。

## 快速示例

```bash
# 路径 A
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
# 平台级账号探活（无学校归属，仅可达平台级学校管理；zsh 下 USERNAME 只读，须用 env）
env USERNAME=platform9001 ROLE=PLATFORM_ADMIN FORCE_LOGIN=true ./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh

# 本地联调（两个终端，默认 MySQL）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh

# 本地联调（单终端一键后台启停，默认 MySQL；DB=h2 用 H2）
./scripts/path-b/dev.sh up
./scripts/path-b/dev.sh status
./scripts/path-b/dev.sh down

# AI 生成：注入 4 个环境变量后启动（不注入时为「未配置」态，不影响其余功能）
./scripts/path-b/dev.sh down   # 必须先停：dev.sh 检测到端口已占用会跳过启动，变量不生效
env COURSEWARE_AI_BASE_URL='https://<网关地址>/v1' COURSEWARE_AI_API_KEY='<密钥>' \
    COURSEWARE_AI_MODEL='<模型名>' COURSEWARE_AI_TIMEOUT_SECONDS=120 \
    ./scripts/path-b/dev.sh up

# H2 文件库（可选）
./scripts/path-b/backend-h2.sh

# 路径 B：新增教师（后端须已启动）
./scripts/common/create-teacher.sh \
  --username teacher-260806 \
  --password 123456 \
  --display-name '王老师-leon'

# 破坏性操作前备份
./scripts/common/backup-local-data.sh --label before-refactor --with-files
./scripts/common/verify-local-backup.sh --latest
./scripts/path-b/reset-h2.sh --label before-reset   # H2 清库（内置 H2 备份）
```

日常开发默认 `./scripts/path-b/backend.sh`（MySQL）；无 MySQL 用 `backend-h2.sh`；整站演示用路径 A。勿并行占用 `8080` / `5173`。
