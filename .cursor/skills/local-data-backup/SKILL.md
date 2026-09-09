---
name: local-data-backup
description: >-
  本地数据备份与恢复（默认 MySQL mysqldump 表结构+数据；可选 H2/files）。
  Flyway/清库/覆盖 MySQL、migrate-h2-to-mysql、用户说备份/恢复/数据丢了 时 invoke；
  与 path-b-data-backup-gate.mdc 配套。path-b-data-backup 技能已合并至此。
---

# 本地数据备份与恢复

> **规则门禁**：`.cursor/rules/path-b-data-backup-gate.mdc`（`alwaysApply`）

## 脚本一览

| 操作 | MySQL（默认） | H2（可选） |
|------|---------------|------------|
| 备份 | `backup-local-data.sh` | `backup-path-b-data.sh`（= `--h2-only`） |
| 恢复 | `restore-local-data.sh` | `restore-path-b-data.sh` |
| 校验 | `verify-local-backup.sh` | `verify-path-b-backup.sh`（转发） |
| 列表 | `backup-local-data.sh --list` | 同上 |

## 何时 invoke

| 场景 | 动作 |
|------|------|
| Flyway 迁移、覆盖 MySQL、migrate-h2-to-mysql | **先** `backup-local-data.sh [--with-files]` |
| H2 清库 | `backup-path-b-data.sh` 或 `--h2-only`，再 `reset-h2.sh` |
| 用户说数据丢了 | `--list` → MySQL:`restore-local-data.sh --latest` |
| 里程碑 | `--label milestone-xxx --with-files` |
| 用户说「备份一下」 | `backup-local-data.sh --with-files` |

## 命令

```bash
./scripts/common/backup-local-data.sh --label before-refactor --with-files
./scripts/common/verify-local-backup.sh --latest
./scripts/common/backup-local-data.sh --list
./scripts/common/restore-local-data.sh --latest --with-files
```

## Agent 流程

1. 恢复 / DROP DATABASE 前 → 停 `./scripts/path-b/backend.sh`
2. `backup-local-data.sh --label <简述> [--with-files] [--h2]`
3. `verify-local-backup.sh --latest`
4. 未成功 → 禁止破坏性操作

## 硬约束

- 禁止 `rm -rf data/h2/`；H2 清库用 `reset-h2.sh`
- MySQL 恢复会 DROP 整个 `teaching_platform`
- `data/backups/` 不进 git

## 文档

`docs/运维文档/2026-09-09-本地数据备份与恢复.md`
