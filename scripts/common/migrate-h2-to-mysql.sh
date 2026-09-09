#!/usr/bin/env bash
# 将路径 B H2 文件库（结构+数据）迁移到本地 MySQL。
# 用法（仓库根目录）：
#   ./scripts/common/migrate-h2-to-mysql.sh
#   DB_PASS=12345678 MYSQL_HOST=127.0.0.1 ./scripts/common/migrate-h2-to-mysql.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${DB_PASS:-12345678}"
MYSQL_DB="${DB_NAME:-teaching_platform}"
H2_FILE="${H2_FILE:-${ROOT_DIR}/data/h2/teaching_platform}"
H2_JAR="${H2_JAR:-${HOME}/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar}"
BACKUP_DIR="${ROOT_DIR}/data/backups"
H2_DUMP="${BACKUP_DIR}/h2_to_mysql_dump.sql"
MYSQL_DUMP="${BACKUP_DIR}/teaching_platform_mysql.sql"
LABEL="${LABEL:-before-mysql-migrate}"

if [[ ! -f "$H2_JAR" ]]; then
  echo "未找到 H2 JAR: $H2_JAR" >&2
  echo "请先在后端目录执行一次 ./mvnw -q dependency:resolve" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "错误: 未找到 mysql 客户端，请先安装 MySQL 命令行工具。" >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "错误: 未找到 python3（转换脚本需要）。" >&2
  exit 1
fi

if lsof -i ":${SERVER_PORT:-8080}" >/dev/null 2>&1; then
  echo "警告: 端口 ${SERVER_PORT:-8080} 仍有后端进程，请先停止后端（path-b/backend.sh 或 backend-h2.sh）。" >&2
  echo "继续可能导致 H2 导出失败或数据不一致。" >&2
  exit 1
fi

./scripts/common/backup-local-data.sh --label "$LABEL" --with-files --h2 --force

mkdir -p "$BACKUP_DIR"

echo "==> 导出 H2: $H2_FILE"
java -cp "$H2_JAR" org.h2.tools.Script \
  -url "jdbc:h2:file:${H2_FILE};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1" \
  -user sa -password "" \
  -script "$H2_DUMP"

echo "==> 转换为 MySQL SQL"
python3 "$ROOT_DIR/scripts/common/h2-dump-to-mysql.py" "$H2_DUMP" "$MYSQL_DUMP"

echo "==> 导入 MySQL: ${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" -e \
  "DROP DATABASE IF EXISTS \`${MYSQL_DB}\`; CREATE DATABASE \`${MYSQL_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" < "$MYSQL_DUMP"

TABLE_COUNT="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DB}';")"
USER_COUNT="$(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -e \
  "SELECT COUNT(*) FROM \`${MYSQL_DB}\`.sys_user;")"

echo "完成: MySQL 库 \`${MYSQL_DB}\` 共 ${TABLE_COUNT} 张表、${USER_COUNT} 个用户"
echo "启动本地后端（MySQL）："
echo "  ./scripts/path-b/backend.sh"
echo "  ./scripts/path-c/frontend.sh"
echo "文档: docs/运维文档/2026-09-09-路径C-本地MySQL联调与H2迁移.md"
echo "SQL 备份: $MYSQL_DUMP"
