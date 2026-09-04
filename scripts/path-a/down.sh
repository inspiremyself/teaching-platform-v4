#!/usr/bin/env bash
# 路径 A：停止 Docker Compose 服务
# 用法:
#   ./scripts/path-a/down.sh           # 保留数据卷
#   ./scripts/path-a/down.sh --volumes # 同时删除 MySQL / 文件存储卷
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

REMOVE_VOLUMES=0
EXTRA_ARGS=()

for arg in "$@"; do
  case "$arg" in
    --volumes|-v)
      REMOVE_VOLUMES=1
      ;;
    *)
      EXTRA_ARGS+=("$arg")
      ;;
  esac
done

echo "==> 停止路径 A（Docker Compose）..."
if [[ "$REMOVE_VOLUMES" -eq 1 ]]; then
  if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    docker compose down -v "${EXTRA_ARGS[@]}"
  else
    docker compose down -v
  fi
  echo "已停止，并删除数据卷。"
else
  if [[ ${#EXTRA_ARGS[@]} -gt 0 ]]; then
    docker compose down "${EXTRA_ARGS[@]}"
  else
    docker compose down
  fi
  echo "已停止（数据卷已保留）。需要清空数据时请加 --volumes。"
fi
