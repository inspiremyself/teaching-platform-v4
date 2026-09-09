#!/usr/bin/env bash
# H2 文件库专用备份入口（转发 backup-local-data.sh --h2-only）
# MySQL 表结构+数据请用: ./scripts/common/backup-local-data.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "${ROOT_DIR}/scripts/common/backup-local-data.sh" --h2-only "$@"
