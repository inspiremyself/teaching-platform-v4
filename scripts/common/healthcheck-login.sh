#!/usr/bin/env bash
# 登录探活：校验后端 /api/v1/auth/login 是否可用
# 可选环境变量:
#   API_BASE=http://localhost:8080/api/v1
#   USERNAME=t9001
#   PASSWORD=123456
#   ROLE=TEACHER
#   FORCE_LOGIN=true          # 默认 true，避免探活撞上「已在其他设备登录」
set -euo pipefail

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
USERNAME="${USERNAME:-t9001}"
PASSWORD="${PASSWORD:-123456}"
ROLE="${ROLE:-TEACHER}"
FORCE_LOGIN="${FORCE_LOGIN:-true}"

URL="${API_BASE%/}/auth/login"
PAYLOAD=$(printf '{"username":"%s","password":"%s","role":"%s","forceLogin":%s}' \
  "$USERNAME" "$PASSWORD" "$ROLE" "$FORCE_LOGIN")

echo "==> POST ${URL}"
echo "    username=${USERNAME} role=${ROLE}"

if ! command -v curl >/dev/null 2>&1; then
  echo "错误: 未找到 curl。" >&2
  exit 1
fi

HTTP_CODE=0
BODY=""
BODY=$(curl -sS -w '\n%{http_code}' -X POST "$URL" \
  -H 'Content-Type: application/json' \
  -d "$PAYLOAD") || {
  echo "错误: 请求失败，请确认后端已启动且地址正确。" >&2
  exit 1
}

HTTP_CODE=$(printf '%s\n' "$BODY" | tail -n 1)
BODY=$(printf '%s\n' "$BODY" | sed '$d')

echo "    HTTP ${HTTP_CODE}"
echo "$BODY"

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "探活失败: HTTP 状态非 200。" >&2
  exit 1
fi

if printf '%s' "$BODY" | grep -q '"code":0'; then
  if printf '%s' "$BODY" | grep -Eqi '"token"|"accessToken"'; then
    echo "探活成功: code=0 且响应含 token。"
    exit 0
  fi
  echo "探活部分成功: code=0，但未检测到 token 字段，请人工核对响应。" >&2
  exit 0
fi

echo "探活失败: 业务 code 非 0。" >&2
exit 1
