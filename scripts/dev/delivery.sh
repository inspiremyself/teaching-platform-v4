#!/usr/bin/env bash
# delivery.sh — 交付报告：commit message 建议 + diff 摘要 + 验收清单（纯 stdout，不写文件）
# 用法: ./scripts/dev/delivery.sh --prepare --spec <方案路径>
# 退出码: 0 报告已输出；2 参数缺失 / spec 文件不存在 / 未找到验收标准节
# 约束: 零 git 写操作（只读 git status / git diff --stat），无推送能力
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
用法: ./scripts/dev/delivery.sh --prepare --spec <方案路径>
  --prepare   生成交付报告（唯一子命令）
  --spec      必传，指向含「验收标准」节的技术方案文件
退出码: 0 报告已输出 / 2 参数缺失、spec 不存在或未找到验收标准节
EOF
  exit "$1"
}

if [ $# -lt 1 ] || [ "$1" != "--prepare" ]; then
  usage 2
fi
shift

SPEC=""
while [ $# -gt 0 ]; do
  case "$1" in
    --spec)
      if [ $# -lt 2 ]; then usage 2; fi
      SPEC="$2"
      shift 2
      ;;
    *) usage 2 ;;
  esac
done

if [ -z "$SPEC" ]; then usage 2; fi
if [ ! -f "$SPEC" ]; then
  echo "spec 文件不存在: ${SPEC}" >&2
  usage 2
fi

# 第 1 节：commit message 建议（按改动路径定作用域）
FE_CHANGES="$(git status --porcelain -- frontend || true)"
BE_CHANGES="$(git status --porcelain -- backend || true)"
scope="fullstack"
if [ -n "$FE_CHANGES" ] && [ -z "$BE_CHANGES" ]; then
  scope="frontend"
elif [ -z "$FE_CHANGES" ] && [ -n "$BE_CHANGES" ]; then
  scope="backend"
fi

echo "## Commit message 建议"
echo "type(${scope}): "
echo "<为什么>"
echo "- <要点 1>"
echo ""

# 第 2 节：diff 摘要
echo "## Diff 摘要"
git diff --stat || true
echo ""
echo "### untracked 文件"
git status --porcelain | awk '/^\?\?/ {print $2}' || true
echo ""

# 第 3 节：验收清单（awk 提取「验收标准」节，逐行转为 - [ ] 条目，跳过空行）
LIST="$(awk '
  /^#{1,3} .*验收标准/ {found=1; next}
  found && /^## / {exit}
  found && NF {print}
' "$SPEC")"
if [ -z "$LIST" ]; then
  echo "spec 中未找到验收标准节" >&2
  exit 2
fi

echo "## 验收清单"
while IFS= read -r line; do
  echo "- [ ] ${line}"
done <<<"$LIST"
exit 0
