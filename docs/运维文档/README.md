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

## 快速示例

```bash
# 路径 A
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh

# 本地联调（两个终端，默认 MySQL）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh

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
