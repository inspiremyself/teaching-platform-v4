#!/usr/bin/env bash
# 本地启动后端（默认 MySQL / local profile）
# 可选环境变量:
#   SERVER_PORT=18080
#   DB_HOST=127.0.0.1
#   DB_PORT=3306
#   DB_NAME=teaching_platform
#   DB_USER=root
#   DB_PASS=12345678          # 路径 A Docker MySQL 为 123456
#   FILE_STORAGE_ROOT
# 无 MySQL 时用 H2：./scripts/path-b/backend-h2.sh
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

export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
export SERVER_PORT="${SERVER_PORT:-8080}"
export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-teaching_platform}"
export DB_USER="${DB_USER:-root}"
export DB_PASS="${DB_PASS:-12345678}"

export FILE_STORAGE_ROOT="${FILE_STORAGE_ROOT:-${ROOT_DIR}/data/files}"
mkdir -p "${FILE_STORAGE_ROOT}"

echo "==> 启动本地后端（MySQL）"
echo "    profile=${SPRING_PROFILES_ACTIVE}"
echo "    port=${SERVER_PORT}"
echo "    jdbc=mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "    user=${DB_USER}"
echo "    工作目录=${BACKEND_DIR}"
echo "    上传根目录: ${FILE_STORAGE_ROOT}"
echo "    首次使用: ./scripts/common/migrate-h2-to-mysql.sh 或手动建库 teaching_platform"
echo "    H2 联调: ./scripts/path-b/backend-h2.sh"

exec ./mvnw spring-boot:run
