#!/usr/bin/env bash
# 本地数据备份（默认 MySQL 表结构+数据；可选上传目录 / H2 文件库）
# 用法:
#   ./scripts/common/backup-local-data.sh
#   ./scripts/common/backup-local-data.sh --with-files --label milestone-xaust
#   ./scripts/common/backup-local-data.sh --h2 --label h2-snapshot
#   ./scripts/common/backup-local-data.sh --list
# 可选环境变量（MySQL，与 path-b/backend.sh 一致）:
#   DB_HOST DB_PORT DB_NAME DB_USER DB_PASS
#   SERVER_PORT H2_DIR FILES_DIR BACKUP_ROOT
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_PORT="${SERVER_PORT:-8080}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-teaching_platform}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-12345678}"
H2_DIR="${H2_DIR:-${ROOT_DIR}/data/h2}"
FILES_DIR="${FILES_DIR:-${ROOT_DIR}/data/files}"
BACKUP_ROOT="${BACKUP_ROOT:-${ROOT_DIR}/data/backups}"

WITH_FILES=false
WITH_H2=false
WITH_MYSQL=true
FORCE=false
LIST=false
LABEL=""

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/backup-local-data.sh [--with-files] [--label <备注>] [--force]
  ./scripts/common/backup-local-data.sh --h2-only [--with-files] [--label <备注>]
  ./scripts/common/backup-local-data.sh --mysql-only [--label <备注>]
  ./scripts/common/backup-local-data.sh --list

说明:
  - 默认备份本机 MySQL 库 teaching_platform（mysqldump：表结构 + 数据 + 触发器/例程）。
  - --with-files 同时备份 data/files/（课程资料、实验答题图）。
  - --h2 在 MySQL 备份基础上额外复制 data/h2/。
  - --h2-only 仅 H2 文件库（等同 backup-path-b-data.sh）。
  - --mysql-only 仅 MySQL，不尝试 H2。
  - 输出: data/backups/<时间戳>[_label]/mysql/teaching_platform.sql
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-files) WITH_FILES=true; shift ;;
    --h2) WITH_H2=true; shift ;;
    --h2-only) WITH_MYSQL=false; WITH_H2=true; shift ;;
    --mysql-only) WITH_MYSQL=true; WITH_H2=false; shift ;;
    --label) LABEL="${2:-}"; shift 2 ;;
    --force) FORCE=true; shift ;;
    --list) LIST=true; shift ;;
    -h|--help) usage; exit 0 ;;
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

mysql_cmd() {
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$@"
}

if [[ "$LIST" == true ]]; then
  if [[ ! -d "$BACKUP_ROOT" ]]; then
    echo "尚无备份（${BACKUP_ROOT} 不存在）。"
    exit 0
  fi
  echo "本地备份列表（${BACKUP_ROOT}）:"
  find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d | sort -r | while read -r dir; do
    name="$(basename "$dir")"
    mysql_size="—"
    h2_size="—"
    files_flag=""
    if [[ -f "${dir}/mysql/teaching_platform.sql" ]]; then
      mysql_size="$(du -sh "${dir}/mysql/teaching_platform.sql" 2>/dev/null | awk '{print $1}')"
    fi
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
    engine="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("engine") or "?")' "${dir}/manifest.json" 2>/dev/null || echo "?")"
    if [[ -n "$label" ]]; then
      echo "  ${name}  engine=${engine} mysql=${mysql_size} h2=${h2_size}${files_flag}  label=${label}"
    else
      echo "  ${name}  engine=${engine} mysql=${mysql_size} h2=${h2_size}${files_flag}"
    fi
  done
  exit 0
fi

if ! command -v mysqldump >/dev/null 2>&1; then
  echo "错误: 未找到 mysqldump，请安装 MySQL 客户端。" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "错误: 未找到 mysql 客户端。" >&2
  exit 1
fi

timestamp="$(date '+%Y-%m-%d_%H%M%S')"
if [[ -n "$LABEL" ]]; then
  backup_dir="${BACKUP_ROOT}/${timestamp}_$(sanitize_label "$LABEL")"
else
  backup_dir="${BACKUP_ROOT}/${timestamp}"
fi

mkdir -p "${backup_dir}/mysql"

echo "==> 本地数据备份"
echo "    目标: ${backup_dir}"

MYSQL_OK=false
if [[ "$WITH_MYSQL" == true ]]; then
  if ! mysql_cmd -e "USE \`${DB_NAME}\`;" >/dev/null 2>&1; then
    echo "错误: 无法连接 MySQL 或库 \`${DB_NAME}\` 不存在（${DB_USER}@${DB_HOST}:${DB_PORT}）。" >&2
    if [[ "$WITH_H2" != true ]]; then
      exit 1
    fi
    echo "    跳过 MySQL（将仅备份 H2/files）。" >&2
  else
    dump_file="${backup_dir}/mysql/teaching_platform.sql"
    echo "    导出 MySQL: ${DB_NAME} → mysql/teaching_platform.sql"
    mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" \
      --single-transaction \
      --routines \
      --triggers \
      --events \
      --hex-blob \
      --set-gtid-purged=OFF \
      --default-character-set=utf8mb4 \
      "$DB_NAME" > "$dump_file"
    if [[ ! -s "$dump_file" ]]; then
      echo "错误: mysqldump 输出为空。" >&2
      exit 1
    fi
    if ! grep -qi 'CREATE TABLE' "$dump_file"; then
      echo "错误: 导出文件缺少 CREATE TABLE，备份无效。" >&2
      exit 1
    fi
    MYSQL_OK=true
    echo "    MySQL 导出完成（含表结构与数据）"
  fi
fi

if [[ "$WITH_H2" == true ]]; then
  if [[ ! -d "$H2_DIR" ]] || ! compgen -G "${H2_DIR}/*.mv.db" >/dev/null; then
    echo "错误: 未找到 H2 文件库（${H2_DIR}/*.mv.db）。" >&2
    exit 1
  fi
  if backend_listening; then
    if [[ "$FORCE" != true ]]; then
      echo "错误: 后端占用端口 ${SERVER_PORT}，H2 备份请先停后端或使用 --force。" >&2
      exit 1
    fi
    echo "警告: 后端仍在运行，H2 备份可能不一致。" >&2
  fi
  mkdir -p "${backup_dir}/h2"
  cp -a "${H2_DIR}/." "${backup_dir}/h2/"
  echo "    已复制 h2/"
fi

if [[ "$WITH_FILES" == true ]]; then
  if [[ -d "$FILES_DIR" ]]; then
    mkdir -p "${backup_dir}/files"
    cp -a "${FILES_DIR}/." "${backup_dir}/files/"
    echo "    已复制 files/"
  else
    echo "    跳过 files/（目录不存在）"
  fi
fi

if [[ "$MYSQL_OK" != true && "$WITH_H2" != true ]]; then
  echo "错误: 未产生任何数据库备份。" >&2
  exit 1
fi

git_head=""
if git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git_head="$(git -C "$ROOT_DIR" rev-parse --short HEAD 2>/dev/null || true)"
fi

python3 - "$backup_dir/manifest.json" "$LABEL" "$WITH_FILES" "$WITH_H2" "$MYSQL_OK" "$DB_HOST" "$DB_PORT" "$DB_NAME" "$DB_USER" "$H2_DIR" "$FILES_DIR" "$git_head" <<'PY'
import json, datetime, os, sys

(
    path,
    label,
    with_files,
    with_h2,
    mysql_ok,
    db_host,
    db_port,
    db_name,
    db_user,
    h2_dir,
    files_dir,
    git_head,
) = sys.argv[1:13]

includes_mysql = mysql_ok == "true"
includes_h2 = with_h2 == "true"
includes_files = with_files == "true"

if includes_mysql:
    engine = "mysql"
elif includes_h2:
    engine = "h2"
else:
    engine = "unknown"

payload = {
    "createdAt": datetime.datetime.now().astimezone().isoformat(timespec="seconds"),
    "label": label or None,
    "engine": engine,
    "includesMysql": includes_mysql,
    "includesH2": includes_h2,
    "includesFiles": includes_files,
    "mysql": {
        "host": db_host,
        "port": int(db_port),
        "database": db_name,
        "user": db_user,
        "dumpFile": "mysql/teaching_platform.sql",
    }
    if includes_mysql
    else None,
    "h2Dir": os.path.abspath(h2_dir) if includes_h2 else None,
    "filesDir": os.path.abspath(files_dir) if includes_files else None,
    "gitHead": git_head or None,
}
with open(path, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")
PY

echo "    manifest.json 已写入"

if [[ "$MYSQL_OK" == true ]]; then
  stats="$(mysql_cmd -N "$DB_NAME" -e "
    SELECT CONCAT(
      'teachers=', SUM(role='TEACHER'),
      ', students=', SUM(role='STUDENT'),
      ', schools=', (SELECT COUNT(*) FROM school),
      ', classes=', (SELECT COUNT(*) FROM class_room),
      ', labSubmissions=', (SELECT COUNT(*) FROM lab_submission)
    ) FROM sys_user;
  " 2>/dev/null || true)"
  if [[ -n "$stats" ]]; then
    python3 - "$backup_dir/manifest.json" "$stats" <<'PY'
import json, re, sys

path, raw = sys.argv[1], sys.argv[2]
pairs = dict(re.findall(r'(\w+)=(\d+)', raw))
if not pairs:
    raise SystemExit(0)
with open(path, encoding="utf-8") as f:
    payload = json.load(f)
payload["stats"] = {k: int(v) for k, v in pairs.items()}
with open(path, "w", encoding="utf-8") as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)
    f.write("\n")
print("    快照统计:", ", ".join(f"{k}={v}" for k, v in payload["stats"].items()))
PY
  fi
fi

echo "完成: ${backup_dir}"
if [[ "$MYSQL_OK" == true ]]; then
  echo "MySQL 恢复: ./scripts/common/restore-local-data.sh --backup ${backup_dir}"
fi
if [[ "$WITH_H2" == true ]]; then
  echo "H2 恢复: ./scripts/common/restore-path-b-data.sh --backup ${backup_dir}"
fi
