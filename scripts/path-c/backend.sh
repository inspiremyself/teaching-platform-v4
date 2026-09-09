#!/usr/bin/env bash
# 路径 C：与 path-b/backend.sh 相同（本机 MySQL，保留路径 C 文档引用）
exec "$(dirname "${BASH_SOURCE[0]}")/../path-b/backend.sh" "$@"
