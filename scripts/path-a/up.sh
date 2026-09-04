#!/usr/bin/env bash
# 路径 A：Docker Compose 后台启动（构建并拉起 MySQL + 后端 + 前端）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "错误: 未找到 docker，请先安装 Docker。" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "错误: 未找到 docker compose，请安装 Compose V2。" >&2
  exit 1
fi

echo "==> 启动路径 A（Docker Compose）..."
docker compose up --build -d "$@"

echo
echo "已后台启动。访问："
echo "  前端: http://localhost:5173/"
echo "  后端: http://localhost:8080/api/v1"
echo
echo "查看日志: ./scripts/path-a/logs.sh"
echo "登录探活: ./scripts/common/healthcheck-login.sh"
echo "停止服务: ./scripts/path-a/down.sh"
