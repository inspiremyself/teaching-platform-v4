#!/usr/bin/env bash
# 路径 C：转调路径 B 前端脚本
exec "$(dirname "${BASH_SOURCE[0]}")/../path-b/frontend.sh" "$@"
