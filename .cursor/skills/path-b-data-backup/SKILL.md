---
name: path-b-data-backup
description: >-
  路径 B 本地 H2 文件库与上传目录的备份、恢复与清库前门禁。在清库/删 data/h2/、Flyway
  重建、数据库迁移联调、重构可能丢数据、用户说备份/恢复/数据丢了/账号没了 时 invoke；
  与 alwaysApply 规则 path-b-data-backup-gate.mdc 配套；执行 backup/restore 脚本，
  禁止未备份就删库。
---

# 路径 B 数据备份与恢复

> **规则门禁**：`.cursor/rules/path-b-data-backup-gate.mdc`（`alwaysApply`）— 破坏性操作前 Agent 须自动备份。

## 何时 invoke

| 场景 | 动作 |
|------|------|
| 用户要删 `data/h2/`、清库、重置 Flyway、跑破坏性迁移 | **先备份**，再操作 |
| 用户说账号/导入数据没了、怀疑重构丢数据 | 列备份 → 尝试 `--latest` 恢复 |
| 大功能开发开始前（校管导入、批量建账号） | 建议 `--with-files` 备份 |
| 用户只说「备份一下」 | 执行备份并回报路径 |

## 数据范围

| 路径 | 内容 | 默认备份 |
|------|------|----------|
| `data/h2/` | H2 文件库（账号、业务数据） | ✅ |
| `data/files/` | 上传文件（资料、实验图） | 仅 `--with-files` |
| 备份落盘 | `data/backups/<时间戳>[_label]/` | gitignore，不进仓库 |

**不会**被 Flyway 种子恢复的内容：脚本/校管/导入写入的账号与学生——清库即丢。

## 命令（仓库根目录）

```bash
# 备份（推荐：停后端后执行）
./scripts/common/backup-path-b-data.sh --label before-refactor
./scripts/common/backup-path-b-data.sh --with-files --label with-uploads

# 列出备份
./scripts/common/backup-path-b-data.sh --list

# 恢复（须停后端）
./scripts/common/restore-path-b-data.sh --latest
./scripts/common/restore-path-b-data.sh --backup data/backups/2026-09-09_094500 --with-files --force
```

## Agent 执行流程

### A. 清库 / 破坏性操作前（门禁）

1. 检查 `lsof` 端口 `${SERVER_PORT:-8080}`：在跑则提示用户停 `./scripts/path-b/backend.sh`。
2. 执行 `./scripts/common/backup-path-b-data.sh --label <操作简述> [--with-files]`。
3. 向用户回报备份目录；**未备份成功禁止**删 `data/h2/` 或建议清库。
4. 若用户坚持不备份，明确告知将丢失手动账号与导入数据。

### B. 数据丢失排查

1. `./scripts/common/backup-path-b-data.sh --list`
2. 查当前库：`sys_user` 是否缺目标账号（H2 Console 或登录 API 40101）。
3. 有合适备份 → 停后端 → `restore-path-b-data.sh --latest`（或指定目录）→ 重启 → `healthcheck-login.sh`。
4. 无备份 → 说明 Flyway 仅保留演示种子；用校管端/`create-teacher.sh` 重建。

### C. 恢复后验证

```bash
./scripts/path-b/backend.sh   # 终端 1
./scripts/common/healthcheck-login.sh
# 必要时对用户账号单独 curl 登录探活
```

## 硬约束

- **禁止** `rm -rf data/h2/`；清库只用 `./scripts/path-b/reset-h2.sh`（内置先备份）。
- 备份后跑 `./scripts/common/verify-path-b-backup.sh --latest` 确认 manifest 与快照统计。
- **后端运行时禁止恢复**；备份默认也拒绝（`--force` 仅应急）。
- 不要提交 `data/` 到 git。
- 跨校改 `school_id` 前须备份，并告知班级/学生不会随用户迁移。
- 演示账号删库后 Flyway 会回来；**自定义账号/导入学生不会**。

## 里程碑备份（建议）

完成以下操作后立刻 `--label milestone-<简述> --with-files`：

- 校管 Excel 导入一批学生
- 脚本/校管创建真实学校与教师
- 实验/作业联调造数后需保留

## 文档

- 运维细节：`docs/运维文档/2026-09-09-路径B-数据备份与恢复.md`
