#!/usr/bin/env bash
# gate.sh — 研发流程门禁：按改动侧跑 lint/test/build 汇总
# 用法：./scripts/dev/gate.sh [--frontend | --backend | --all | --help]
# 退出码：0 全部通过（含双侧 [SKIP]）；1 任一命令失败；2 参数错误
# 说明：无参数时按 git status --porcelain 判侧，只跑有改动侧；显式参数强制跑、无 [SKIP]
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
用法: ./scripts/dev/gate.sh [--frontend | --backend | --all | --help]
  (无参数)     按 git status --porcelain 判侧，只跑有改动侧
  --frontend   强制跑前端侧 npm run lint / npm test / npm run build
  --backend    强制跑后端侧 ./mvnw test
  --all        强制跑两侧
  --help       打印本用法
退出码: 0 通过(含双侧 [SKIP]) / 1 失败 / 2 参数错误
EOF
  exit "$1"
}

MODE=""
MODE_COUNT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --frontend|--backend|--all)
      if [ -n "$MODE" ]; then usage 2; fi
      MODE="$1"
      MODE_COUNT=$((MODE_COUNT + 1))
      ;;
    --help) usage 0 ;;
    *) usage 2 ;;
  esac
  shift
done
if [ "$MODE_COUNT" -gt 1 ]; then usage 2; fi

FE_RUN=0
BE_RUN=0
case "$MODE" in
  --frontend) FE_RUN=1 ;;
  --backend) BE_RUN=1 ;;
  --all) FE_RUN=1; BE_RUN=1 ;;
  *)
    FE_CHANGES="$(git status --porcelain -- frontend || true)"
    BE_CHANGES="$(git status --porcelain -- backend || true)"
    if [ -n "$FE_CHANGES" ]; then FE_RUN=1; fi
    if [ -n "$BE_CHANGES" ]; then BE_RUN=1; fi
    if [ "$FE_RUN" -eq 0 ]; then echo "[SKIP] frontend 无改动"; fi
    if [ "$BE_RUN" -eq 0 ]; then echo "[SKIP] backend 无改动"; fi
    ;;
esac

FAILED_CMD=""
FAILED_RC=0

run_backend() {
  cd backend
  echo "[GATE] backend ./mvnw test 开始"
  rc=0
  ./mvnw test || rc=$?
  if [ "$rc" -ne 0 ]; then
    echo "[GATE] backend ./mvnw test FAIL(退出码 ${rc})"
    FAILED_CMD="backend ./mvnw test"
    FAILED_RC="$rc"
  else
    echo "[GATE] backend ./mvnw test OK"
  fi
  cd "$ROOT_DIR"
}

if [ "$FE_RUN" -eq 1 ]; then
  cd frontend
  for c in "npm run lint" "npm test" "npm run build"; do
    echo "[GATE] frontend ${c} 开始"
    rc=0
    eval "$c" || rc=$?
    if [ "$rc" -ne 0 ]; then
      echo "[GATE] frontend ${c} FAIL(退出码 ${rc})"
      FAILED_CMD="frontend ${c}"
      FAILED_RC="$rc"
      break
    fi
    echo "[GATE] frontend ${c} OK"
  done
  cd "$ROOT_DIR"
fi

if [ "$BE_RUN" -eq 1 ] && [ -z "$FAILED_CMD" ]; then
  run_backend
fi

if [ -n "$FAILED_CMD" ]; then
  echo "[FAIL] gate: ${FAILED_CMD} 退出码 ${FAILED_RC}"
  exit 1
fi

echo "[PASS] gate"
exit 0
