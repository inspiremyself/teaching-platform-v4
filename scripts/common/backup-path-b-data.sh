#!/usr/bin/env bash
# 路径 B：备份 H2 文件库（及可选上传目录）
# 用法:
#   ./scripts/common/backup-path-b-data.sh
#   ./scripts/common/backup-path-b-data.sh --with-files
#   ./scripts/common/backup-path-b-data.sh --label before-refactor --with-files
#   ./scripts/common/backup-path-b-data.sh --list
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

WITH_FILES=false
FORCE=false
LIST=false
LABEL=""

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/backup-path-b-data.sh [--with-files] [--label <备注>] [--force]
  ./scripts/common/backup-path-b-data.sh --list

说明:
  - 默认仅备份 data/h2/（H2 文件库）。
  - --with-files 同时备份 data/files/（课程资料、实验答题图等上传）。
  - 后端占用 H2 文件锁时默认拒绝备份；加 --force 可强制复制（有损坏风险，仅应急）。
  - 备份目录: data/backups/<时间戳>[_<label>]/
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-files)
      WITH_FILES=true
      shift
      ;;
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --list)
      LIST=true
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

sanitize_label() {
  printf '%s' "$1" | tr ' /\\:' '____'
}

backend_listening() {
  lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1
}

if [[ "$LIST" == true ]]; then
  if [[ ! -d "$BACKUP_ROOT" ]]; then
    echo "尚无备份（${BACKUP_ROOT} 不存在）。"
    exit 0
  fi
  echo "路径 B 备份列表（${BACKUP_ROOT}）:"
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | sort -r | while read -r dir; do
    name="$(basename "$dir")"
    h2_size="0"
    files_flag=""
    if [[ -d "${dir}/h2" ]]; then
      h2_size="$(du -sh "${dir}/h2" 2>/dev/null | awk '{print $1}')"
    fi
    if [[ -d "${dir}/files" ]]; then
      files_flag=" +files"
    fi
    label=""
    if [[ -f "${dir}/manifest.json" ]] && command -v python3 >/dev/null 2>&1; then
      label="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("label") or "")' "${dir}/manifest.json" 2>/dev/null || true)"
    fi
    if [[ -n "$label" ]]; then
      echo "  ${name}  h2=${h2_size}${files_flag}  label=${label}"
    else
      echo "  ${name}  h2=${h2_size}${files_flag}"
    fi
  done
  exit 0
fi

if [[ ! -d "$H2_DIR" ]] || ! compgen -G "${H2_DIR}/*.mv.db" >/dev/null; then
  echo "错误: 未找到 H2 文件库（${H2_DIR}/*.mv.db）。请先启动路径 B 后端生成库，或确认 H2_DIR。" >&2
  exit 1
fi

if backend_listening; then
  if [[ "$FORCE" != true ]]; then
    echo "错误: 检测到后端占用端口 ${SERVER_PORT}。" >&2
    echo "请先停止路径 B 后端再备份，或使用 --force 强制复制（不推荐）。" >&2
    exit 1
  fi
  echo "警告: 后端仍在运行，备份可能不一致；建议停后端后重试。" >&2
fi

timestamp="$(date '+%Y-%m-%d_%H%M%S')"
if [[ -n "$LABEL" ]]; then
  backup_dir="${BACKUP_ROOT}/${timestamp}_$(sanitize_label "$LABEL")"
else
  backup_dir="${BACKUP_ROOT}/${timestamp}"
fi

mkdir -p "$backup_dir/h2"
echo "==> 路径 B 备份"
echo "    目标: ${backup_dir}"

cp -a "${H2_DIR}/." "${backup_dir}/h2/"
echo "    已复制 h2/"

if [[ "$WITH_FILES" == true ]]; then
  if [[ -d "$FILES_DIR" ]]; then
    mkdir -p "${backup_dir}/files"
    cp -a "${FILES_DIR}/." "${backup_dir}/files/"
    echo "    已复制 files/"
  else
    echo "    跳过 files/（目录不存在）"
  fi
fi

git_head=""
if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git_head="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
fi

python3 - "$backup_dir/manifest.json" "$LABEL" "$WITH_FILES" "$H2_DIR" "$FILES_DIR" "$git_head" <<'PY'
import json, datetime, os, sys

path, label, with_files, h2_dir, files_dir, git_head = sys.argv[1:7]
payload = {
    "createdAt": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
    "label": label or None,
    "includesFiles": with_files == "true",
    "h2Dir": os.path.abspath(h2_dir),
    "filesDir": os.path.abspath(files_dir) if with_files == "true" else None,
    "gitHead": git_head or None,
}
with open(path, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

echo "    manifest.json 已写入"

capture_backup_stats() {
  if ! lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
    return 0
  fi
  if ! command -v curl >/dev/null 2>&1; then
    return 0
  fi
  local h2_console="${H2_CONSOLE:-http://localhost:8080/h2-console}"
  h2_console="${h2_console%/}"
  local backup_h2_file="${backup_dir}/h2/teaching_platform"
  local db_url="jdbc:h2:file:${backup_h2_file};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;ACCESS_MODE_DATA=r"
  local cookie jsession idx login_html out

  cookie="$(mktemp -t backup-stats-cookie.XXXXXX)"
  idx="$(curl -sS -c "$cookie" -b "$cookie" "${h2_console}/")" || { rm -f "$cookie"; return 0; }
  jsession="$(printf '%s' "$idx" | grep -oE 'jsessionid=[a-f0-9]+' | head -1 | cut -d= -f2 || true)"
  if [[ -z "${jsession:-}" ]]; then
    rm -f "$cookie"
    return 0
  fi
  curl -sS -c "$cookie" -b "$cookie" -o /dev/null "${h2_console}/login.jsp?jsessionid=${jsession}"
  login_html="$(curl -sS -c "$cookie" -b "$cookie" -X POST "${h2_console}/login.do?jsessionid=${jsession}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "language=en" \
    --data-urlencode "setting=Generic H2 (Embedded)" \
    --data-urlencode "name=Generic H2 (Embedded)" \
    --data-urlencode "driver=org.h2.Driver" \
    --data-urlencode "url=${db_url}" \
    --data-urlencode "user=sa" \
    --data-urlencode "password=")"
  if ! printf '%s' "$login_html" | grep -qi 'frameset\|query.jsp'; then
    rm -f "$cookie"
    return 0
  fi
  out="$(curl -sS -c "$cookie" -b "$cookie" -X POST "${h2_console}/query.do?jsessionid=${jsession}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "sql=SELECT (SELECT COUNT(*) FROM sys_user WHERE role='TEACHER') AS teachers, (SELECT COUNT(*) FROM sys_user WHERE role='STUDENT') AS students, (SELECT COUNT(*) FROM school) AS schools, (SELECT COUNT(*) FROM class_room) AS classes, (SELECT COUNT(*) FROM lab_submission) AS lab_submissions;")"
  rm -f "$cookie"
  python3 - "$backup_dir/manifest.json" "$out" <<'PY'
import json, re, sys, html as H

manifest_path, raw = sys.argv[1], sys.argv[2]
text = H.unescape(re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", re.sub(r"<script[\s\S]*?</script>", "", raw, flags=re.I))).strip())
m = re.search(
    r"teachers\s+students\s+schools\s+classes\s+lab_submissions\s+"
    r"(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)",
    text,
    flags=re.I,
)
if not m:
    raise SystemExit(0)
keys = ["teachers", "students", "schools", "classes", "labSubmissions"]
stats = dict(zip(keys, map(int, m.groups())))
with open(manifest_path, encoding="utf-8") as f:
    payload = json.load(f)
payload["stats"] = stats
with open(manifest_path, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("    快照统计:", ", ".join(f"{k}={v}" for k, v in stats.items()))
PY
}

capture_backup_stats || true

echo "完成: ${backup_dir}"
echo "恢复: ./scripts/common/restore-path-b-data.sh --backup ${backup_dir}"
