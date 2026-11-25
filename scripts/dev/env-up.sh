#!/usr/bin/env bash
# env-up.sh — 环境验收：端口检查 → 未就绪则启动 → 归属预检 → healthcheck → 前端探活
# 用法: ./scripts/dev/env-up.sh
# 环境变量: BACKEND_PORT (默认 8080)、FRONTEND_PORT (默认 5173)
# 退出码: 0 两服务就绪；1 失败（[FAIL] 必带 [ENV] 或 [CODE] 之一）
# 约束: 不 kill 任何进程、不改端口配置、不使用 mock 服务
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_LOG=/tmp/teaching-platform-dev-backend.log
FRONTEND_LOG=/tmp/teaching-platform-dev-frontend.log
BACKEND_ALREADY=0
FRONTEND_ALREADY=0

port_busy() {
  [ -n "$(lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null || true)" ]
}

port_first_pid() {
  lsof -nP -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -n1
}

# 1. 后端端口检测：未监听则启动并轮询等待
if port_busy "$BACKEND_PORT"; then
  BACKEND_ALREADY=1
else
  SERVER_PORT="$BACKEND_PORT" nohup bash scripts/path-b/backend.sh >"$BACKEND_LOG" 2>&1 &
  echo "backend started pid=$!"
  backend_ready=0
  for _ in $(seq 1 60); do
    if port_busy "$BACKEND_PORT"; then backend_ready=1; break; fi
    sleep 3
  done
  if [ "$backend_ready" -ne 1 ]; then
    occ="$(port_first_pid "$BACKEND_PORT" || true)"
    if [ -n "$occ" ]; then occ_desc="占用方: ${occ}"; else occ_desc="占用方: 无"; fi
    echo "[FAIL] [ENV] 后端 ${BACKEND_PORT} 端口未就绪（${occ_desc}），日志 ${BACKEND_LOG}"
    exit 1
  fi
fi

# 2. 后端归属预检（仅在端口已有监听时执行）：
#    POST 空 body → HTTP 400 且响应体含 "code":40000（本平台统一响应参数校验指纹）= 本平台后端
if [ "$BACKEND_ALREADY" -eq 1 ]; then
  resp="$(curl -s -w '\n%{http_code}' -X POST "http://localhost:${BACKEND_PORT}/api/v1/auth/login" -H 'Content-Type: application/json' -d '{}' || true)"
  code="$(printf '%s' "$resp" | tail -n1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  if [ "$code" != "400" ] || ! printf '%s' "$body" | grep -q '"code":40000'; then
    echo "[FAIL] [ENV] 端口 ${BACKEND_PORT} 被非本平台进程占用（占用方 PID: $(port_first_pid "$BACKEND_PORT" || true)）"
    exit 1
  fi
fi

# 3. 后端健康验证：healthcheck 登录（默认教师账号，脚本内置 FORCE_LOGIN=true）
if ! bash scripts/common/healthcheck-login.sh; then
  echo "[FAIL] [CODE] healthcheck 登录失败（HTTP/业务码见上）"
  exit 1
fi

# 4. 前端端口检测：未监听则启动并轮询等待
if port_busy "$FRONTEND_PORT"; then
  FRONTEND_ALREADY=1
else
  VITE_API_TARGET="http://localhost:${BACKEND_PORT}" nohup bash scripts/path-b/frontend.sh >"$FRONTEND_LOG" 2>&1 &
  echo "frontend started pid=$!"
  frontend_ready=0
  for _ in $(seq 1 20); do
    if curl -sf -o /dev/null "http://localhost:${FRONTEND_PORT}/"; then frontend_ready=1; break; fi
    sleep 3
  done
  if [ "$frontend_ready" -ne 1 ]; then
    echo "[FAIL] [ENV] 前端 ${FRONTEND_PORT} 端口未就绪（占用方: $(port_first_pid "$FRONTEND_PORT" || true)），日志 ${FRONTEND_LOG}"
    exit 1
  fi
fi

# 5. 前端 HTTP 探活
fe_code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${FRONTEND_PORT}/" || true)"
if [ "$fe_code" != "200" ]; then
  echo "[FAIL] [ENV] 前端 HTTP 非 200"
  exit 1
fi

# 汇总输出
be_state="started"
fe_state="started"
if [ "$BACKEND_ALREADY" -eq 1 ]; then be_state="already"; fi
if [ "$FRONTEND_ALREADY" -eq 1 ]; then fe_state="already"; fi
if [ "$BACKEND_ALREADY" -eq 1 ] && [ "$FRONTEND_ALREADY" -eq 1 ]; then
  echo "[ALREADY_RUNNING]"
fi
echo "[PASS] env-up（backend: ${be_state}, frontend: ${fe_state}）"
exit 0
