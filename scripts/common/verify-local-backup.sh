#!/usr/bin/env bash
# 校验本地备份（MySQL dump / H2 / manifest / 可选 files）
# 用法:
#   ./scripts/common/verify-local-backup.sh --latest
#   ./scripts/common/verify-local-backup.sh --backup data/backups/2026-09-09_103000_label
# 历史别名: verify-path-b-backup.sh（转发本脚本）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/data/backups}"

BACKUP=""
LATEST=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/verify-local-backup.sh --backup <备份目录>
  ./scripts/common/verify-local-backup.sh --latest

说明:
  - MySQL：检查 mysql/teaching_platform.sql 含 CREATE TABLE 与 INSERT
  - H2：检查 h2/*.mv.db（若备份含 H2）
  - 均需 manifest.json
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) BACKUP="${2:-}"; shift 2 ;;
    --latest) LATEST=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *)
      echo "错误: 未知参数: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

resolve_backup_dir() {
  local dir="$1"
  if [[ -z "$dir" ]]; then
    echo "错误: 请指定 --backup 或 --latest。" >&2
    exit 1
  fi
  if [[ "$dir" != /* ]]; then
    dir="${ROOT_DIR}/${dir#./}"
  fi
  if [[ ! -d "$dir" ]]; then
    echo "错误: 备份目录不存在: ${dir}" >&2
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
DUMP="${BACKUP}/mysql/teaching_platform.sql"
MANIFEST="${BACKUP}/manifest.json"
MV_DB="$(find "${BACKUP}/h2" -maxdepth 1 -name '*.mv.db' 2>/dev/null | head -n 1 || true)"

echo "==> 校验备份: ${BACKUP}"
errors=0

if [[ ! -f "$MANIFEST" ]]; then
  echo "❌ 缺少 manifest.json" >&2
  errors=$((errors + 1))
else
  echo "✅ manifest.json"
fi

has_mysql=false
has_h2=false

if [[ -f "$DUMP" ]]; then
  has_mysql=true
  if [[ ! -s "$DUMP" ]]; then
    echo "❌ mysql/teaching_platform.sql 为空" >&2
    errors=$((errors + 1))
  else
    create_count="$(grep -ci '^CREATE TABLE' "$DUMP" || true)"
    insert_count="$(grep -ci '^INSERT INTO' "$DUMP" || true)"
    dump_size="$(du -sh "$DUMP" | awk '{print $1}')"
    if [[ "${create_count:-0}" -lt 1 ]]; then
      echo "❌ dump 中无 CREATE TABLE（缺少表结构）" >&2
      errors=$((errors + 1))
    else
      echo "✅ mysql/teaching_platform.sql  size=${dump_size}  CREATE TABLE=${create_count}  INSERT INTO=${insert_count}"
    fi
  fi
fi

if [[ -n "$MV_DB" && -f "$MV_DB" ]]; then
  has_h2=true
  if [[ ! -s "$MV_DB" ]]; then
    echo "❌ h2/*.mv.db 为空" >&2
    errors=$((errors + 1))
  else
    size="$(du -sh "$MV_DB" | awk '{print $1}')"
    echo "✅ h2 文件: $(basename "$MV_DB") (${size})"
  fi
elif [[ -d "${BACKUP}/h2" ]]; then
  echo "❌ h2/ 存在但缺少 *.mv.db" >&2
  errors=$((errors + 1))
  has_h2=true
fi

if [[ "$has_mysql" != true && "$has_h2" != true ]]; then
  echo "❌ 备份中既无 MySQL dump 也无 H2 文件库" >&2
  errors=$((errors + 1))
fi

if [[ -d "${BACKUP}/files" ]]; then
  echo "✅ files/ 已包含"
else
  echo "ℹ️  未包含 files/"
fi

if [[ "$errors" -gt 0 ]]; then
  echo "校验失败 (${errors} 项)" >&2
  exit 1
fi

if command -v python3 >/dev/null 2>&1 && [[ -f "$MANIFEST" ]]; then
  python3 - "$MANIFEST" <<'PY'
import json, sys

with open(sys.argv[1], encoding="utf-8") as f:
    m = json.load(f)
print("   engine:", m.get("engine"))
print("   label:", m.get("label"))
if m.get("includesMysql"):
    print("   includesMysql: true")
if m.get("includesH2"):
    print("   includesH2: true")
if m.get("includesFiles"):
    print("   includesFiles: true")
if m.get("stats"):
    print("   stats:", ", ".join(f"{k}={v}" for k, v in m["stats"].items()))
PY
fi

echo "校验通过。"
