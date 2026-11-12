#!/usr/bin/env bash
# 路径 B：对外演示（frp 内网穿透）启动入口；日常开发用 scripts/path-b/dev.sh
# 用法: ./scripts/path-b/dev-public.sh up
#       ./scripts/path-b/dev-public.sh down
#       ./scripts/path-b/dev-public.sh status
#       ./scripts/path-b/dev-public.sh logs backend
#       ./scripts/path-b/dev-public.sh logs frontend
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export SERVER_COMPRESSION_ENABLED=true
export APP_HTTP_ACCESS_LOG_ENABLED=false
export SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20
export SERVER_TOMCAT_THREADS_MAX=80
export LOGGING_LEVEL_COM_OPENCODE_TEACHINGPLATFORM=INFO

echo "==> 对外演示模式（frp dev）注入："
printf '    %s=%s\n' \
  SERVER_COMPRESSION_ENABLED "${SERVER_COMPRESSION_ENABLED}" \
  APP_HTTP_ACCESS_LOG_ENABLED "${APP_HTTP_ACCESS_LOG_ENABLED}" \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE "${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE}" \
  SERVER_TOMCAT_THREADS_MAX "${SERVER_TOMCAT_THREADS_MAX}" \
  LOGGING_LEVEL_COM_OPENCODE_TEACHINGPLATFORM "${LOGGING_LEVEL_COM_OPENCODE_TEACHINGPLATFORM}"

exec "${ROOT_DIR}/scripts/path-b/dev.sh" "$@"
