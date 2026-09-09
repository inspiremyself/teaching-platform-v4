#!/usr/bin/env bash
# 路径 B：从备份恢复 H2 文件库（及可选上传目录）
# 用法:
#   ./scripts/common/restore-path-b-data.sh --backup data/backups/2026-09-09_094500
#   ./scripts/common/restore-path-b-data.sh --latest
#   ./scripts/common/restore-path-b-data.sh --latest --with-files
# 可选环境变量:
#   SERVER_PORT=8080
#   H2_DIR=<仓库根>/data/h2
#   FILES_DIR=<仓库根>/data/files
#   BACKUP_ROOT=<仓库根>/data/backups
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_PORT="${SERVER_PORT:-8080}"
H2_DIR="${H2_DIR:-${ROOT_DIR}/data/h2}"
FILES_DIR="${FILES_DIR:-${ROOT_DIR}/data/files}"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/data/backups}"

BACKUP=""
LATEST=false
WITH_FILES=false
FORCE=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/restore-path-b-data.sh --backup <备份目录>
  ./scripts/common/restore-path-b-data.sh --latest [--with-files] [--force]

说明:
  - 恢复前须停止路径 B 后端（占用 H2 文件锁）。
  - --latest 取 BACKUP_ROOT 下最新目录。
  - --with-files 同时恢复 files/（仅当备份中含 files/ 时生效）。
  - 当前 data/h2/（及 files/）会被覆盖；加 --force 跳过确认。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup)
      BACKUP="${2:-}"
      shift 2
      ;;
    --latest)
      LATEST=true
      shift
      ;;
    --with-files)
      WITH_FILES=true
      shift
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
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
  if [[ ! -d "${dir}/h2" ]] || ! compgen -G "${dir}/h2/*.mv.db" >/dev/null; then
    echo "错误: 备份无效（缺少 h2/*.mv.db）: ${dir}" >&2
    exit 1
  fi
  printf '%s' "$dir"
}

if [[ "$LATEST" == true ]]; then
  if [[ ! -d "$BACKUP_ROOT" ]]; then
    echo "错误: 尚无备份目录 ${BACKUP_ROOT}。" >&2
    exit 1
  fi
  BACKUP="$(find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | sort -r | head -n 1)"
  if [[ -z "$BACKUP" ]]; then
    echo "错误: ${BACKUP_ROOT} 下无备份。" >&2
    exit 1
  fi
fi

BACKUP="$(resolve_backup_dir "$BACKUP")"

if backend_listening; then
  echo "错误: 检测到后端占用端口 ${SERVER_PORT}，请先停止路径 B 后端再恢复。" >&2
  exit 1
fi

if [[ "$FORCE" != true ]]; then
  echo "即将覆盖:"
  echo "  ${H2_DIR}/  ← ${BACKUP}/h2/"
  if [[ "$WITH_FILES" == true && -d "${BACKUP}/files" ]]; then
    echo "  ${FILES_DIR}/ ← ${BACKUP}/files/"
  fi
  read -r -p "确认恢复？[y/N] " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    echo "已取消。"
    exit 0
  fi
fi

mkdir -p "$H2_DIR"
rm -f "${H2_DIR}/"*
cp -a "${BACKUP}/h2/." "${H2_DIR}/"
echo "==> 已恢复 h2/"

if [[ "$WITH_FILES" == true ]]; then
  if [[ -d "${BACKUP}/files" ]]; then
    mkdir -p "$FILES_DIR"
    rm -rf "${FILES_DIR:?}/"*
    cp -a "${BACKUP}/files/." "${FILES_DIR}/"
    echo "==> 已恢复 files/"
  else
    echo "警告: 备份中无 files/，跳过。" >&2
  fi
fi

if [[ -f "${BACKUP}/manifest.json" ]]; then
  echo "==> manifest"
  cat "${BACKUP}/manifest.json"
fi

echo "完成: 已从 ${BACKUP} 恢复。"
echo "下一步: ./scripts/path-b/backend.sh 启动后端，并用 healthcheck-login.sh 探活。"
