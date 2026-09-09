#!/usr/bin/env bash
# 路径 B：安全清空 H2 文件库（先自动备份，再删除 data/h2/）
# 用法:
#   ./scripts/path-b/reset-h2.sh
#   ./scripts/path-b/reset-h2.sh --with-files --label before-v14
#   ./scripts/path-b/reset-h2.sh --force
# 禁止直接 rm -rf data/h2/；请用本脚本。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SERVER_PORT="${SERVER_PORT:-8080}"

WITH_FILES=true
LABEL="before-reset"
FORCE=false

usage() {
  cat <<'EOF'
用法:
  ./scripts/path-b/reset-h2.sh [--with-files] [--label <备注>] [--force]

说明:
  1. 检查后端已停止（占用 H2 文件锁时拒绝继续）
  2. 自动执行 backup-path-b-data.sh（仅 H2；MySQL 备份请用 backup-local-data.sh）
  3. 确认后删除 data/h2/ 下文件（下次启动 Flyway 重灌演示种子）
  4. 不会删除 data/backups/

反例:
  - 不要直接 rm -rf data/h2/
  - 不要在后端运行时清库
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-files)
      WITH_FILES=true
      shift
      ;;
    --no-files)
      WITH_FILES=false
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

if lsof -nP -iTCP:"${SERVER_PORT}" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "错误: 检测到后端占用端口 ${SERVER_PORT}，请先停止 ./scripts/path-b/backend.sh。" >&2
  exit 1
fi

backup_args=(--label "$LABEL")
if [[ "$WITH_FILES" == true ]]; then
  backup_args+=(--with-files)
fi

echo "==> 步骤 1/3：自动备份"
"${ROOT_DIR}/scripts/common/backup-path-b-data.sh" "${backup_args[@]}"

if [[ "$FORCE" != true ]]; then
  echo
  echo "即将删除: ${ROOT_DIR}/data/h2/*"
  echo "Flyway 将只恢复演示种子（t9001 / 20260001 / admin9001 等）。"
  echo "手动账号、校管导入学生、实验提交等业务数据将丢失（已写入上方备份）。"
  read -r -p "确认清库？[y/N] " answer
  if [[ ! "$answer" =~ ^[Yy]$ ]]; then
    echo "已取消清库。"
    exit 0
  fi
fi

echo "==> 步骤 2/3：删除 H2 文件库"
mkdir -p "${ROOT_DIR}/data/h2"
rm -f "${ROOT_DIR}/data/h2/"*
echo "    已清空 data/h2/"

echo "==> 步骤 3/3：完成"
echo "下一步: ./scripts/path-b/backend.sh  # Flyway 重灌种子"
echo "恢复备份: ./scripts/common/restore-path-b-data.sh --latest"
