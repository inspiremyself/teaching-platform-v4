#!/usr/bin/env bash
# 路径 A：查看 Compose 日志
# 用法:
#   ./scripts/path-a/logs.sh           # 全部服务
#   ./scripts/path-a/logs.sh backend   # 指定服务: mysql | backend | frontend
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

echo "==> 跟踪路径 A 日志（Ctrl+C 退出）..."
docker compose logs -f "$@"
