#!/usr/bin/env bash
# 路径 B：本地启动前端（Vite）
# 可选环境变量:
#   VITE_API_TARGET=http://127.0.0.1:18080
# 额外参数会传给 npm run dev，例如:
#   ./scripts/path-b/frontend.sh -- --port 18081 --host 127.0.0.1
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
cd "$FRONTEND_DIR"

if ! command -v npm >/dev/null 2>&1; then
  echo "错误: 未找到 npm，请安装 Node.js。" >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "==> 未检测到 node_modules，执行 npm install..."
  npm install
fi

API_TARGET="${VITE_API_TARGET:-http://localhost:8080}"
echo "==> 启动路径 B 前端"
echo "    VITE_API_TARGET=${API_TARGET}"
echo "    工作目录=${FRONTEND_DIR}"
echo "    默认访问: http://localhost:5173/"

export VITE_API_TARGET="$API_TARGET"
exec npm run dev -- "$@"
