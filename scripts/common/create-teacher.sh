#!/usr/bin/env bash
# 路径 B：在已启动的后端上新增教师账号（经 H2 Console 写文件库）
# 用法:
#   ./scripts/common/create-teacher.sh \
#     --username teacher-260806 \
#     --password 123456 \
#     --display-name '王老师-leon'
# 可选环境变量:
#   API_BASE=http://localhost:8080/api/v1
#   H2_CONSOLE=http://localhost:8080/h2-console
#   H2_FILE=<仓库根>/data/h2/teaching_platform   # JDBC 文件路径（无 .mv.db 后缀）
# 可选参数:
#   --force   用户名已存在时跳过插入，只做登录探活
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
H2_CONSOLE="${H2_CONSOLE:-http://localhost:8080/h2-console}"
H2_FILE="${H2_FILE:-${ROOT_DIR}/data/h2/teaching_platform}"

USERNAME=""
PASSWORD=""
DISPLAY_NAME=""
FORCE=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/common/create-teacher.sh \
    --username <用户名> \
    --password <明文密码> \
    --display-name <显示名> \
    [--force]

说明:
  - 仅路径 B：后端须已用 test profile 启动，且占用 H2 文件库。
  - 密码入库为 {noop}<明文>，与种子账号一致；登录仍用明文。
  - --force：用户名已存在则跳过 INSERT，仅探活登录。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --username)
      USERNAME="${2:-}"
      shift 2
      ;;
    --password)
      PASSWORD="${2:-}"
      shift 2
      ;;
    --display-name)
      DISPLAY_NAME="${2:-}"
      shift 2
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

if [[ -z "$USERNAME" || -z "$PASSWORD" || -z "$DISPLAY_NAME" ]]; then
  echo "错误: --username / --password / --display-name 均为必填。" >&2
  usage >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "错误: 未找到 curl。" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "错误: 未找到 python3（用于解析 H2 Console HTML）。" >&2
  exit 1
fi

# 禁止改演示教师账号语义：若有人误用 t9001 创建，直接拒绝（除非只是 --force 探活且已存在）
if [[ "$USERNAME" == "t9001" ]]; then
  echo "错误: 演示教师账号 t9001 请勿用本脚本改写；探活请用 healthcheck-login.sh。" >&2
  exit 1
fi

H2_CONSOLE="${H2_CONSOLE%/}"
DB_URL="jdbc:h2:file:${H2_FILE};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
COOKIE="$(mktemp -t create-teacher-cookie.XXXXXX)"
cleanup() { rm -f "$COOKIE"; }
trap cleanup EXIT

echo "==> 路径 B 添加教师"
echo "    username=${USERNAME}"
echo "    displayName=${DISPLAY_NAME}"
echo "    h2Console=${H2_CONSOLE}"
echo "    h2File=${H2_FILE}"

# --- H2 Console helpers (Tomcat 对 ;jsessionid= 路径参数会 403，统一用 ?jsessionid=) ---
h2_bootstrap() {
  local idx html
  idx="$(curl -sS -c "$COOKIE" -b "$COOKIE" "${H2_CONSOLE}/")" || {
    echo "错误: 无法访问 H2 Console，请确认路径 B 后端已启动。" >&2
    exit 1
  }
  JSESSION="$(printf '%s' "$idx" | grep -oE 'jsessionid=[a-f0-9]+' | head -1 | cut -d= -f2 || true)"
  if [[ -z "${JSESSION:-}" ]]; then
    echo "错误: 未从 H2 Console 拿到 jsessionid。" >&2
    exit 1
  fi
  # 确保 Cookie 带上会话
  if ! grep -q JSESSIONID "$COOKIE" 2>/dev/null; then
    printf 'localhost\tFALSE\t/\tFALSE\t0\tJSESSIONID\t%s\n' "$JSESSION" >>"$COOKIE"
  fi
  curl -sS -c "$COOKIE" -b "$COOKIE" -o /dev/null \
    "${H2_CONSOLE}/login.jsp?jsessionid=${JSESSION}"
  html="$(curl -sS -c "$COOKIE" -b "$COOKIE" -X POST \
    "${H2_CONSOLE}/login.do?jsessionid=${JSESSION}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "language=en" \
    --data-urlencode "setting=Generic H2 (Embedded)" \
    --data-urlencode "name=Generic H2 (Embedded)" \
    --data-urlencode "driver=org.h2.Driver" \
    --data-urlencode "url=${DB_URL}" \
    --data-urlencode "user=sa" \
    --data-urlencode "password=")"
  if ! printf '%s' "$html" | grep -qi 'frameset\|query.jsp'; then
    echo "错误: H2 Console 登录失败（请核对 H2_FILE 与后端是否同一文件库）。" >&2
    printf '%s\n' "$html" | head -c 400 >&2
    echo >&2
    exit 1
  fi
}

h2_sql() {
  local sql="$1"
  local out
  out="$(curl -sS -c "$COOKIE" -b "$COOKIE" -X POST \
    "${H2_CONSOLE}/query.do?jsessionid=${JSESSION}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "sql=${sql}")"
  python3 - "$out" <<'PY'
import html as H, re, sys
raw = sys.argv[1]
text = re.sub(r"<script[\s\S]*?</script>", "", raw, flags=re.I)
text = re.sub(r"<[^>]+>", " ", text)
text = H.unescape(re.sub(r"\s+", " ", text)).strip()
print(text)
PY
}

h2_bootstrap

# H2 Console 结果会回显 SQL 文本，不能靠 grep 用户名判断是否命中行；用行数标记。
# 有数据: "(1 row, …)"；无数据: "(no rows, …)" 或 "(0 rows, …)"
h2_row_count() {
  python3 -c '
import re, sys
t = sys.argv[1]
if re.search(r"\(\s*no\s+rows?\b", t, flags=re.I):
    print(0)
    raise SystemExit(0)
m = re.search(r"\((\d+)\s+rows?\b", t, flags=re.I)
print(m.group(1) if m else "")
' "$1"
}

echo "==> 检查用户名是否已存在"
UN_ESC="${USERNAME//\'/\'\'}"
EXIST_TEXT="$(h2_sql "SELECT id, username, role, display_name, status FROM sys_user WHERE username='${UN_ESC}';")"
EXIST_ROWS="$(h2_row_count "$EXIST_TEXT")"
if [[ -z "$EXIST_ROWS" ]]; then
  echo "错误: 无法解析查询结果行数。" >&2
  echo "    ${EXIST_TEXT}" >&2
  exit 1
fi
if [[ "$EXIST_ROWS" != "0" ]]; then
  if [[ "$FORCE" != true ]]; then
    echo "错误: 用户名已存在（${EXIST_ROWS} 行）。若只需验证登录可加 --force。" >&2
    echo "    ${EXIST_TEXT}" >&2
    exit 1
  fi
  echo "    已存在，--force：跳过插入"
else
  # 转义单引号（SQL 字面量）
  PW_ESC="${PASSWORD//\'/\'\'}"
  DN_ESC="${DISPLAY_NAME//\'/\'\'}"
  INSERT_SQL=$(cat <<EOF
INSERT INTO sys_user (
  username, password_hash, role, display_name, status,
  must_change_password, created_at, updated_at
) VALUES (
  '${UN_ESC}', '{noop}${PW_ESC}', 'TEACHER', '${DN_ESC}', 'ACTIVE',
  FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
EOF
)
  echo "==> INSERT sys_user"
  INS_TEXT="$(h2_sql "$INSERT_SQL")"
  if ! printf '%s' "$INS_TEXT" | grep -qi 'Update count: 1'; then
    echo "错误: 插入失败。" >&2
    echo "    ${INS_TEXT}" >&2
    exit 1
  fi
  echo "    ${INS_TEXT}"

  VERIFY_TEXT="$(h2_sql "SELECT id, username, role, display_name, status FROM sys_user WHERE username='${UN_ESC}';")"
  echo "==> 插入后核对"
  echo "    ${VERIFY_TEXT}"
fi

echo "==> 登录探活"
LOGIN_URL="${API_BASE%/}/auth/login"
# JSON 转义
json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.argv[1]))' "$1"
}
PAYLOAD=$(printf '{"username":%s,"password":%s,"role":"TEACHER","forceLogin":true}' \
  "$(json_escape "$USERNAME")" "$(json_escape "$PASSWORD")")

HTTP_BODY="$(curl -sS -w '\n%{http_code}' -X POST "$LOGIN_URL" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD")" || {
  echo "错误: 登录请求失败。" >&2
  exit 1
}
HTTP_CODE="$(printf '%s\n' "$HTTP_BODY" | tail -n 1)"
BODY="$(printf '%s\n' "$HTTP_BODY" | sed '$d')"
echo "    POST ${LOGIN_URL} -> HTTP ${HTTP_CODE}"
echo "$BODY"

if [[ "$HTTP_CODE" != "200" ]] || ! printf '%s' "$BODY" | grep -q '"code":0'; then
  echo "错误: 登录探活失败。" >&2
  exit 1
fi
if ! printf '%s' "$BODY" | grep -Eqi '"token"|"accessToken"'; then
  echo "警告: code=0 但未检测到 token，请人工核对。" >&2
  exit 0
fi

echo "完成: 教师 ${USERNAME} 可用（role=TEACHER，明文密码登录）。"
