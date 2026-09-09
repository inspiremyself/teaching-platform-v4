#!/usr/bin/env bash
# 校验路径 B 备份是否可用（结构 + manifest + 可选 H2 快照统计）
# 用法:
#   ./scripts/common/verify-path-b-backup.sh --latest
#   ./scripts/common/verify-path-b-backup.sh --backup data/backups/2026-09-09_095624_before-xaust-school
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/data/backups}"
H2_CONSOLE="${H2_CONSOLE:-http://localhost:8080/h2-console}"
SERVER_PORT="${SERVER_PORT:-8080}"

BACKUP=""
LATEST=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/verify-path-b-backup.sh --backup <备份目录>
  ./scripts/common/verify-path-b-backup.sh --latest

说明:
  - 检查 h2/*.mv.db、manifest.json 是否存在且非空
  - 若后端已启动，经 H2 Console 只读连接备份副本并输出 sys_user 等计数
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
MV_DB="$(find "${BACKUP}/h2" -maxdepth 1 -name '*.mv.db' | head -n 1)"

echo "==> 校验备份: ${BACKUP}"

errors=0
if [[ -z "$MV_DB" || ! -f "$MV_DB" ]]; then
  echo "❌ 缺少 h2/*.mv.db" >&2
  errors=$((errors + 1))
elif [[ ! -s "$MV_DB" ]]; then
  echo "❌ h2/*.mv.db 为空" >&2
  errors=$((errors + 1))
else
  size="$(du -sh "$MV_DB" | awk '{print $1}')"
  echo "✅ h2 文件: $(basename "$MV_DB") (${size})"
fi

if [[ ! -f "${BACKUP}/manifest.json" ]]; then
  echo "❌ 缺少 manifest.json" >&2
  errors=$((errors + 1))
else
  echo "✅ manifest.json"
  if command -v python3 >/dev/null 2>&1; then
    python3 - "${BACKUP}/manifest.json" <<'PY'
import json, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
print(f"    createdAt={m.get('createdAt')}")
print(f"    label={m.get('label')}")
print(f"    includesFiles={m.get('includesFiles')}")
stats = m.get("stats") or {}
if stats:
    print(f"    stats: teachers={stats.get('teachers')} students={stats.get('students')} schools={stats.get('schools')} classes={stats.get('classes')} labSubmissions={stats.get('labSubmissions')}")
PY
  fi
fi

if [[ -d "${BACKUP}/files" ]]; then
  echo "✅ files/ 已包含"
else
  echo "ℹ️  未包含 files/（仅 h2 备份）"
fi

if [[ "$errors" -gt 0 ]]; then
  echo "校验失败 (${errors} 项)" >&2
  exit 1
fi

if ! lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ℹ️  后端未启动，跳过 H2 快照统计（结构校验已通过）。"
  echo "完成: 备份结构有效。"
  exit 0
fi

if ! command -v curl >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
  echo "ℹ️  缺少 curl/python3，跳过 H2 快照统计。"
  echo "完成: 备份结构有效。"
  exit 0
fi

BACKUP_H2_FILE="${BACKUP}/h2/teaching_platform"
DB_URL="jdbc:h2:file:${BACKUP_H2_FILE};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;ACCESS_MODE_DATA=r"
COOKIE="$(mktemp -t verify-backup-cookie.XXXXXX)"
cleanup() { rm -f "$COOKIE"; }
trap cleanup EXIT

H2_CONSOLE="${H2_CONSOLE%/}"
idx="$(curl -sS -c "$COOKIE" -b "$COOKIE" "${H2_CONSOLE}/")"
JSESSION="$(printf '%s' "$idx" | grep -oE 'jsessionid=[a-f0-9]+' | head -1 | cut -d= -f2 || true)"
if [[ -z "${JSESSION:-}" ]]; then
  echo "ℹ️  无法连接 H2 Console，跳过快照统计。"
  echo "完成: 备份结构有效。"
  exit 0
fi

curl -sS -c "$COOKIE" -b "$COOKIE" -o /dev/null "${H2_CONSOLE}/login.jsp?jsessionid=${JSESSION}"
login_html="$(curl -sS -c "$COOKIE" -b "$COOKIE" -X POST "${H2_CONSOLE}/login.do?jsessionid=${JSESSION}" \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode "language=en" \
  --data-urlencode "setting=Generic H2 (Embedded)" \
  --data-urlencode "name=Generic H2 (Embedded)" \
  --data-urlencode "driver=org.h2.Driver" \
  --data-urlencode "url=${DB_URL}" \
  --data-urlencode "user=sa" \
  --data-urlencode "password=")"

if ! printf '%s' "$login_html" | grep -qi 'frameset\|query.jsp'; then
  echo "ℹ️  H2 Console 无法打开备份副本，跳过快照统计。"
  echo "完成: 备份结构有效。"
  exit 0
fi

h2_sql() {
  curl -sS -c "$COOKIE" -b "$COOKIE" -X POST "${H2_CONSOLE}/query.do?jsessionid=${JSESSION}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "sql=$1" | python3 -c "
import html as H, re, sys
raw = sys.stdin.read()
text = re.sub(r'<script[\s\S]*?</script>', '', raw, flags=re.I)
text = re.sub(r'<[^>]+>', ' ', text)
print(H.unescape(re.sub(r'\s+', ' ', text)).strip())
"
}

stats_text="$(h2_sql "SELECT (SELECT COUNT(*) FROM sys_user WHERE role='TEACHER') AS teachers, (SELECT COUNT(*) FROM sys_user WHERE role='STUDENT') AS students, (SELECT COUNT(*) FROM school) AS schools, (SELECT COUNT(*) FROM class_room) AS classes, (SELECT COUNT(*) FROM lab_submission) AS lab_submissions;")"
echo "✅ 备份副本快照:"
echo "    ${stats_text}"
echo "完成: 备份结构有效。"
