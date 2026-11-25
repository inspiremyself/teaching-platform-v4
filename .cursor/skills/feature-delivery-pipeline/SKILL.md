---
name: feature-delivery-pipeline
description: 当用户说「完整实施 / 按方案开发 / 走完研发流程 / 从需求到交付」，或要求从需求/技术方案自动推进到自查收敛、门禁、环境验收与交付报告时使用。按 Stage 0–6 状态机编排研发全流程，复用 feature-dev-workflow.mdc 规范与 self-review-and-fix 自查技能，调用 scripts/dev 门禁脚本。
---

# feature-delivery-pipeline

## 第 1 节 规范来源与复用规则

1. 流程规范唯一来源为 `.cursor/rules/feature-dev-workflow.mdc`；自查唯一执行入口为 `.cursor/skills/self-review-and-fix/SKILL.md`；本技能不复制其条款。
2. invoke 任一技能 = 先 Read 该 SKILL.md 全文，再按对应模式执行。
3. 每阶段开始与结束用 `todo_write` 标记状态；未达硬门槛禁止推进下一阶段。
4. 语义判断（方案/实现是否收敛）只由自查技能判定，脚本只提供确定性动作与退出码；禁止为推进阶段放宽 K 门槛、备份门禁或验收标准。

## 第 2 节 Stage 0–6 状态机表

| Stage | 名称 | 进入条件 | 动作 | 硬门槛（未满足禁止推进） | 失败去向 |
|---|---|---|---|---|---|
| 0 | 输入判定 | 用户给出需求描述或指定入口 | 判定三态：缺 PRD → 提示用户补充需求，不臆造 PRD；有 PRD 缺方案 → 起草进 Stage 1；有已定稿方案且无实现 diff → Stage 2；已有实现 diff → Stage 3 | 判定结果已写入 `todo_write` | 判定歧义时停下问用户 |
| 1 | 方案收敛 | 需要起草或修订技术方案 | 按 `tech-spec-decisive.mdc` 写 `docs/技术方案/YYYY-MM-DD-<主题>.md` → invoke `self-review-and-fix` 方案模式 | 技能判定收敛且方案文首为「已定稿」 | 未收敛继续开轮，禁止进 Stage 2 |
| 2 | 编码 | Stage 1 收敛 | 严格按方案文件清单编码；按 `code-conventions.mdc` §2 路由读对应细则；遇 Flyway / 清库 / 覆盖库先过备份门禁 | 只改方案列出文件；备份门禁已过（如涉及） | 范围外需求停下问用户 |
| 3 | 实现收敛 | 编码完成 | invoke `self-review-and-fix` 实现模式 | 技能判定收敛 | 未收敛继续开轮，禁止进 Stage 4 |
| 4 | 门禁 | Stage 3 收敛 | `bash scripts/dev/gate.sh`（无参数，按 `git status --porcelain` 判侧；仅端到端验证需强制单侧时用显式参数） | 退出码 0 且 stdout 含 `[PASS]` | FAIL → 回 Stage 3，问题点重新计数 |
| 5 | 环境验收 | Stage 4 PASS | `bash scripts/dev/env-up.sh` | 退出码 0 且 stdout 含 `[PASS]` | `[ENV]` → 暂停并提示用户处理，用户处理后从 Stage 5 重试；`[CODE]` → 回 Stage 3 重新计数 |
| 6 | 交付 | Stage 5 PASS | `bash scripts/dev/delivery.sh --prepare --spec <方案路径>` → 对输出验收清单逐条标注「需浏览器人工验收」或「本次已自动验证」→ 展示报告 | 报告已展示 | 缺 `--spec` 退出非 0 → 补参数重跑 |

## 第 3 节 硬门槛与失败回退

1. 门禁 FAIL 与 Stage 5 `[CODE]` FAIL 回 Stage 3（实现自查），问题点重新计数。
2. Stage 5 `[ENV]` FAIL 暂停并提示用户处理（如停掉占用进程），不回 Stage 3、不重新计实现问题点；用户处理后从 Stage 5 重试。
3. 全流程不执行 `git push`、不 `kill` 进程、不改端口配置。

## 第 4 节 脚本调用契约

- `scripts/dev/gate.sh`：无参数按 `git status --porcelain` 判侧（无改动侧输出 `[SKIP]`）；`--frontend` / `--backend` / `--all` 强制跑指定侧；`--help` 打印用法。退出码：0 通过（含双侧 `[SKIP]`）、1 失败、2 参数错误。
- `scripts/dev/env-up.sh`：可覆盖 `BACKEND_PORT`（默认 8080）与 `FRONTEND_PORT`（默认 5173）。退出码：0 两服务就绪、1 失败（`[FAIL]` 必带 `[ENV]` 或 `[CODE]` 之一）。
- `scripts/dev/delivery.sh --prepare --spec <方案路径>`：`--spec` 必传。退出码：0 报告已输出、2 参数缺失 / spec 文件不存在 / 未找到验收标准节。
- 输出前缀全集：`[PASS]` / `[FAIL]` / `[SKIP]` / `[ALREADY_RUNNING]` / `[GATE]` / `[ENV]` / `[CODE]`。
- 只读退出码判定结果，禁止把命令输出当「已通过」证据；禁止现场拼 lint/test/build/启动命令，一律走上述脚本。

## 第 5 节 交付边界

- Stage 6 展示报告后流程结束；commit message 建议遵循「写为什么」。
- 提交与推送由用户自行执行；本技能与三脚本均不执行 `git push`（现网用户级 `permissions.deny` 已禁 `git push`）。
- 全流程不 `kill` 任何进程、不改端口配置、不使用 mock 服务。
