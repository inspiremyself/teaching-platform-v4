#!/usr/bin/env bash
# 历史别名：校验 H2 备份 → 统一使用 verify-local-backup.sh
exec "$(dirname "${BASH_SOURCE[0]}")/verify-local-backup.sh" "$@"
