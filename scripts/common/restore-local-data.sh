#!/usr/bin/env bash
# 从备份恢复本机 MySQL（及可选上传目录）
# 用法:
#   ./scripts/common/restore-local-data.sh --latest
#   ./scripts/common/restore-local-data.sh --backup data/backups/2026-09-09_103000_label --with-files
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_PORT="${SERVER_PORT:-8080}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-teaching_platform}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-12345678}"
FILES_DIR="${FILES_DIR:-${ROOT_DIR}/data/files}"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/data/backups}"

BACKUP=""
LATEST=false
WITH_FILES=false
FORCE=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/restore-local-data.sh --backup <备份目录>
  ./scripts/common/restore-local-data.sh --latest [--with-files] [--force]

说明:
  - 恢复前须停止后端（占用 8080）。
  - 会 DROP 并重建 MySQL 库 teaching_platform，再导入 mysql/teaching_platform.sql。
  - --with-files 同时恢复 files/（备份中含 files/ 时）。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) BACKUP="${2:-}"; shift 2 ;;
    --latest) LATEST=true; shift ;;
    --with-files) WITH_FILES=true; shift ;;
    --force) FORCE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "错误: 未知参数: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

backend_listening() {
  lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1
}

mysql_cmd() {
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$@"
}

resolve_backup_dir() {
  local dir="$1"
  if [[ -z "$dir" ]]; then
    echo "错误: 请指定 --backup <目录> 或 --latest。" >&2
    usage >&2
    exit 1
  fi
  if [[ "$dir" != /* ]]; then
    dir="${ROOT_DIR}/${dir#./}"
  fi
  if [[ ! -d "$dir" ]]; then
    echo "错误: 备份目录不存在: ${dir}" >&2
    exit 1
  fi
  if [[ ! -f "${dir}/mysql/teaching_platform.sql" ]]; then
    echo "错误: 备份无效（缺少 mysql/teaching_platform.sql）: ${dir}" >&2
    exit 1
  fi
  printf '%s' "$dir"
}

if [[ "$LATEST" == true ]]; then
  if [[ ! -d "$BACKUP_ROOT" ]]; then
    echo "错误: 尚无备份目录 ${BACKUP_ROOT}。" >&2
    exit 1
  fi
  BACKUP="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -exec test -f '{}/mysql/teaching_platform.sql' \; -print | sort -r | head -n 1)"
  if [[ -z "$BACKUP" ]]; then
    echo "错误: ${BACKUP_ROOT} 下无含 MySQL  dump 的备份。" >&2
    exit 1
  fi
fi

BACKUP="$(resolve_backup_dir "$BACKUP")"
DUMP_FILE="${BACKUP}/mysql/teaching_platform.sql"

if backend_listening; then
  echo "错误: 检测到后端占用端口 ${SERVER_PORT}，请先停止后端再恢复。" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "错误: 未找到 mysql 客户端。" >&2
  exit 1
fi

if [[ "$FORCE" != true ]]; then
  echo "即将覆盖 MySQL 库 \`${DB_NAME}\` ← ${DUMP_FILE}"
  if [[ "$WITH_FILES" == true && -d "${BACKUP}/files" ]]; then
    echo "  ${FILES_DIR}/ ← ${BACKUP}/files/"
  fi
  read -r -p "确认恢复？[y/N] " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    echo "已取消。"
    exit 0
  fi
fi

echo "==> 恢复 MySQL: ${DB_NAME}"
mysql_cmd -e "DROP DATABASE IF EXISTS \`${DB_NAME}\`; CREATE DATABASE \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql_cmd "$DB_NAME" < "$DUMP_FILE"
echo "    已导入 mysql/teaching_platform.sql"

if [[ "$WITH_FILES" == true ]]; then
  if [[ -d "${BACKUP}/files" ]]; then
    mkdir -p "$FILES_DIR"
    rm -rf "${FILES_DIR:?}/"*
    cp -a "${BACKUP}/files/." "$FILES_DIR/"
    echo "==> 已恢复 files/"
  else
    echo "警告: 备份中无 files/，跳过。" >&2
  fi
fi

if [[ -f "${BACKUP}/manifest.json" ]]; then
  echo "==> manifest"
  cat "${BACKUP}/manifest.json"
fi

echo "完成: 已从 ${BACKUP} 恢复 MySQL。"
echo "下一步: ./scripts/path-b/backend.sh 启动后端，并用 healthcheck-login.sh 探活。"
