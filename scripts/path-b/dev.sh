#!/usr/bin/env bash
# 路径 B：一键后台启动 / 停止前后端联调环境
# 用法:
#   ./scripts/path-b/dev.sh up               # 后台启动前端 + 后端（默认 MySQL）
#   DB=h2 ./scripts/path-b/dev.sh up         # 后端改用 H2 文件库（backend-h2.sh）
#   ./scripts/path-b/dev.sh down             # 停止前后端
#   ./scripts/path-b/dev.sh status           # 查看端口 / 进程 / 日志尾部
#   ./scripts/path-b/dev.sh logs backend     # 跟踪后端日志（frontend 同理，缺省 all 交替不可行，请指定）
# 可选环境变量（透传给子脚本）:
#   SERVER_PORT=18080                        # 后端端口（默认 8080）
#   FRONTEND_PORT=18081                      # 前端端口（默认 5173）
#   VITE_API_TARGET=http://127.0.0.1:18080   # 前端代理目标（缺省随 SERVER_PORT 自动推导）
#   DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASS  # MySQL 连接（默认 127.0.0.1:3306/root/12345678）
#   DB=h2                                    # 后端使用 H2 文件库
#   SKIP_PROBE=true                          # up 后跳过登录探活
# 日志: data/logs/backend.log、data/logs/frontend.log
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOG_DIR="${ROOT_DIR}/data/logs"
BACKEND_LOG="${LOG_DIR}/backend.log"
FRONTEND_LOG="${LOG_DIR}/frontend.log"
BACKEND_PID_FILE="${LOG_DIR}/backend.pid"
FRONTEND_PID_FILE="${LOG_DIR}/frontend.pid"

SERVER_PORT="${SERVER_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
BACKEND_WAIT_SEC="${BACKEND_WAIT_SEC:-180}"
FRONTEND_WAIT_SEC="${FRONTEND_WAIT_SEC:-60}"

mkdir -p "${LOG_DIR}"

usage() {
  sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

port_pid() { # 输出监听端口的进程 pid（可能为空）
  # 端口空闲时 lsof 返回非零；在 set -euo pipefail 下必须用 || true 兜底，
  # 否则调用处赋值（pids="$(port_pid ...)") 会直接终结脚本。
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | tr '\n' ' ' | sed 's/ $//' || true
}

is_up() { [[ -n "$(port_pid "$1")" ]]; }

start_backend() {
  if is_up "${SERVER_PORT}"; then
    echo "==> 后端端口 ${SERVER_PORT} 已被占用（pid $(port_pid "${SERVER_PORT}")），跳过启动"
    return 0
  fi
  local backend_script="${ROOT_DIR}/scripts/path-b/backend.sh"
  [[ "${DB:-}" == "h2" ]] && backend_script="${ROOT_DIR}/scripts/path-b/backend-h2.sh"
  echo "==> 后台启动后端: ${backend_script}（profile: ${DB:-mysql}，端口 ${SERVER_PORT}）"
  nohup "${backend_script}" > "${BACKEND_LOG}" 2>&1 &
  echo $! > "${BACKEND_PID_FILE}"
  echo "    pid=$(cat "${BACKEND_PID_FILE}") 日志=${BACKEND_LOG}"
}

start_frontend() {
  if is_up "${FRONTEND_PORT}"; then
    echo "==> 前端端口 ${FRONTEND_PORT} 已被占用（pid $(port_pid "${FRONTEND_PORT}")），跳过启动"
    return 0
  fi
  # 端口非默认时自动对齐代理目标，避免前端仍指向 8080
  if [[ -z "${VITE_API_TARGET:-}" && "${SERVER_PORT}" != "8080" ]]; then
    export VITE_API_TARGET="http://localhost:${SERVER_PORT}"
    echo "==> 自动设置 VITE_API_TARGET=${VITE_API_TARGET}"
  fi
  echo "==> 后台启动前端: scripts/path-b/frontend.sh（端口 ${FRONTEND_PORT}）"
  nohup "${ROOT_DIR}/scripts/path-b/frontend.sh" > "${FRONTEND_LOG}" 2>&1 &
  echo $! > "${FRONTEND_PID_FILE}"
  echo "    pid=$(cat "${FRONTEND_PID_FILE}") 日志=${FRONTEND_LOG}"
}

wait_http() { # $1=url $2=超时秒 $3=名称
  local url="$1" timeout="$2" name="$3" waited=0
  while (( waited < timeout )); do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    if [[ "$code" != "000" ]]; then
      echo "==> ${name} 已就绪（HTTP ${code}，等待 ${waited}s）"
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  echo "错误: ${name} 在 ${timeout}s 内未就绪，请查看日志: ${BACKEND_LOG} / ${FRONTEND_LOG}" >&2
  return 1
}

stop_port() { # $1=端口 $2=名称
  local pids
  pids="$(port_pid "$1")"
  if [[ -z "${pids}" ]]; then
    echo "==> ${2}（端口 $1）未在运行"
    return 0
  fi
  echo "==> 停止 ${2}（端口 $1，pid ${pids}）"
  kill ${pids} 2>/dev/null || true
  local waited=0
  while (( waited < 15 )) && [[ -n "$(port_pid "$1")" ]]; do
    sleep 1
    waited=$((waited + 1))
  done
  if [[ -n "$(port_pid "$1")" ]]; then
    echo "    温和终止超时，强制 kill -9"
    kill -9 $(port_pid "$1") 2>/dev/null || true
  fi
  rm -f "$3"
  echo "    已停止"
}

cmd_up() {
  start_backend
  start_frontend
  echo "==> 等待服务就绪..."
  wait_http "http://localhost:${SERVER_PORT}/api/v1/auth/me" "${BACKEND_WAIT_SEC}" "后端" || exit 1
  wait_http "http://localhost:${FRONTEND_PORT}/" "${FRONTEND_WAIT_SEC}" "前端" || exit 1
  if [[ "${SKIP_PROBE:-false}" != "true" ]]; then
    echo "==> 登录探活"
    if API_BASE="http://localhost:${SERVER_PORT}/api/v1" "${ROOT_DIR}/scripts/common/healthcheck-login.sh"; then
      :
    else
      echo "警告: 登录探活失败，服务端口已监听，请人工查看日志。" >&2
    fi
  fi
  echo "==> 完成: 前端 http://localhost:${FRONTEND_PORT}/  后端 http://localhost:${SERVER_PORT}/api/v1"
}

cmd_down() {
  stop_port "${FRONTEND_PORT}" "前端" "${FRONTEND_PID_FILE}"
  stop_port "${SERVER_PORT}" "后端" "${BACKEND_PID_FILE}"
}

cmd_status() {
  for spec in "后端:${SERVER_PORT}" "前端:${FRONTEND_PORT}"; do
    local name="${spec%%:*}" port="${spec##*:}"
    if is_up "${port}"; then
      echo "==> ${name}: 运行中（端口 ${port}，pid $(port_pid "${port}")）"
    else
      echo "==> ${name}: 未运行（端口 ${port} 空闲）"
    fi
  done
  echo "--- backend.log 尾部 ---"
  tail -n 5 "${BACKEND_LOG}" 2>/dev/null || echo "(无日志)"
  echo "--- frontend.log 尾部 ---"
  tail -n 5 "${FRONTEND_LOG}" 2>/dev/null || echo "(无日志)"
}

cmd_logs() {
  local target="${1:-}"
  case "${target}" in
    backend)  exec tail -n 100 -f "${BACKEND_LOG}" ;;
    frontend) exec tail -n 100 -f "${FRONTEND_LOG}" ;;
    *) echo "用法: $0 logs backend|frontend" >&2; exit 1 ;;
  esac
}

case "${1:-}" in
  up)     cmd_up ;;
  down)   cmd_down ;;
  status) cmd_status ;;
  logs)   shift; cmd_logs "$@" ;;
  *)      usage ;;
esac
