#!/usr/bin/env bash
# 重建 IDE 规则目录到 .cursor/rules 的软链接引用（幂等，可重复执行）。
#   .trae/rules                          目录级链接 -> ../.cursor/rules
#   .codebuddy/rules/<name>/RULE.mdc    文件级链接 -> ../../../.cursor/rules/<name>.mdc
# .trae/ 与 .codebuddy/ 均被 .gitignore 忽略，clone / 换机器后需执行本脚本重建。
# 安全约束：只新增缺失链接，绝不删除或覆盖任何已有文件、目录、链接。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [ ! -d .cursor/rules ]; then
  echo "错误：未找到 .cursor/rules，请在仓库根目录执行。" >&2
  exit 1
fi

# $1 = 链接目标（相对链接所在目录），$2 = 链接路径
link_target() {
  local target="$1" link="$2" current
  if [ -L "$link" ]; then
    current="$(readlink "$link")"
    if [ "$current" = "$target" ]; then
      echo "OK      $link -> $current"
    else
      echo "WARN    $link -> $current（期望 $target），未改动，请人工确认"
    fi
  elif [ -e "$link" ]; then
    echo "WARN    $link 是实体目录/文件，未覆盖"
  else
    mkdir -p "$(dirname "$link")"
    ln -s "$target" "$link"
    echo "CREATED $link -> $target"
  fi
}

# Trae：目录级链接，新增 .mdc 会自动被读到，无需逐个建链
link_target "../.cursor/rules" ".trae/rules"
link_target "../.cursor/skills" ".trae/skills"

# CodeBuddy：skills 为目录级链接，rules 为一规则一目录的文件级链接
link_target "../.cursor/skills" ".codebuddy/skills"
for rule_file in .cursor/rules/*.mdc; do
  name="$(basename "$rule_file" .mdc)"
  link_target "../../../.cursor/rules/$name.mdc" ".codebuddy/rules/$name/RULE.mdc"
done

# 只提示孤儿链接，不删除
for link in .codebuddy/rules/*/RULE.mdc; do
  [ -L "$link" ] || continue
  name="$(basename "$(dirname "$link")")"
  if [ ! -f ".cursor/rules/$name.mdc" ]; then
    echo "WARN    孤儿链接 $link（.cursor/rules 中已无对应规则），未删除"
  fi
done

total="$(find .cursor/rules -maxdepth 1 -name '*.mdc' | wc -l | tr -d ' ')"
linked="$(find .codebuddy/rules -name 'RULE.mdc' -type l | wc -l | tr -d ' ')"
echo "完成：.cursor/rules 共 ${total} 条规则；.codebuddy 文件级链接 ${linked} 条；.trae/rules 为目录级链接。"
