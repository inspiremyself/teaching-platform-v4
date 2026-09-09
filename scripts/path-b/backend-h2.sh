#!/usr/bin/env bash
# 路径 B（H2）：本地启动后端（test profile / H2 文件库）
# 日常默认已改为 MySQL，见 ./backend.sh；仅无 MySQL 或需 H2 联调时使用本脚本。
# 可选环境变量:
#   SERVER_PORT=18080
#   SPRING_PROFILES_ACTIVE=test
#   FILE_STORAGE_ROOT / H2_FILE
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
cd "$BACKEND_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "错误: 未找到 java，请安装 JDK 21。" >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "错误: 未找到 mvn。本仓库 mvnw 依赖系统 Maven，请先安装并加入 PATH。" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-test}"
export SERVER_PORT="${SERVER_PORT:-8080}"

export FILE_STORAGE_ROOT="${FILE_STORAGE_ROOT:-${ROOT_DIR}/data/files}"
export H2_FILE="${H2_FILE:-${ROOT_DIR}/data/h2/teaching_platform}"
mkdir -p "${ROOT_DIR}/data/h2" "${ROOT_DIR}/data/files"

echo "==> 启动路径 B 后端（H2 文件库）"
echo "    profile=${SPRING_PROFILES_ACTIVE}"
echo "    port=${SERVER_PORT}"
echo "    工作目录=${BACKEND_DIR}"
echo "    H2 文件库: ${H2_FILE}.*（清库请用 ../scripts/path-b/reset-h2.sh）"
echo "    上传根目录: ${FILE_STORAGE_ROOT}"

exec ./mvnw spring-boot:run
