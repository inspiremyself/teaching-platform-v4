# CodeBuddy 权限免确认与本地安全边界配置 技术方案

## 1. 文档信息

| 项 | 内容 |
|---|---|
| 文档版本 | V1.2（方案自查第 1–2 轮收敛；执行阶段按用户 2026-11-12 指令回写；实现自查第 3 轮修正验收判据、备份幂等与判定链口径） |
| 编写日期 | 2026-11-12 |
| 状态 | **已定稿（决策写死，可直接编码）**；方案模式自查第 1–2 轮已收敛（§9 修订记录）；V1.2 仅回写执行主体、验收判据、备份幂等与判定链说明，§3.1 JSON 与 7 条 `deny` 零改动 |
| 目标 | 本机 macOS 上 CodeBuddy 执行读、写、命令、网络等工具时不再弹审批确认；同时保住本仓库对 `data/`、Git 外发、提权与规则目录的硬约束 |
| 适用范围 | 本机用户级 CodeBuddy 配置 + CodeBuddy IDE 会话；CLI 会话因共用 user settings 一并生效。不覆盖其它机器、不覆盖团队成员 |
| 上位依据 | 官方 [权限模式](https://www.codebuddy.ai/docs/zh/cli/permission-modes)、[权限规则](https://www.codebuddy.cn/docs/cli/permissions)、[IDE 概览](https://www.codebuddy.ai/docs/zh/ide/User-guide/Overview) |
| 规范引用 | 方案写法 `tech-spec-decisive.mdc`；流程 `feature-dev-workflow.mdc` / `code-conventions.mdc` §2；数据保护 `protect-data-directory.mdc`、`path-b-no-direct-delete.mdc`；备份 `path-b-data-backup-gate.mdc`。本方案不改前后端代码，不触发 `code-conventions.mdc` §2 中后端 / 前端 / 测试细则 |
| 编码门禁 | 方案自查收敛 → 方可实施；方案阶段交付物仅为本文档。V1.1 执行阶段产生用户级配置 diff，按 `feature-dev-workflow.mdc` §2.1 做实现自查；因无前后端代码改动，不进入 §3 构建验证 |

### 1.1 已定决策清单

| 编号 | 决策点 | 唯一结论 |
|---|---|---|
| D1 | 交付范围 | 只做两件事：① 在 `~/.codebuddy/settings.json` 增量新增 `permissions` 对象；② 把 IDE 会话权限档位切到最宽松一档。仓库内业务代码、测试、迁移、脚本、规则、文档一律零改动 |
| D2 | 唯一权限模式 | 用户级 `permissions.defaultMode = "bypassPermissions"`；IDE 会话显式切到 `fullAccess`（界面最宽松档）。不启用 `CODEBUDDY_IS_SANDBOX=1`、不传 `-y` / `--dangerously-skip-permissions`；本方案不新增 `disableBypassPermissionsMode` 字段，若其它作用域已存在 `"disable"`，按 §4 步骤 3 清除，不保留该字段 |
| D3 | 达到「不弹确认」的机制（按会话类型写死） | **IDE 会话**依赖三项：`permissions.allow = ["Bash"]`（user 作用域可信 allow，越过交互态危险命令检查）+ `defaultMode = bypassPermissions`（bypass 阶段放行其余工具）+ 会话 `fullAccess`（跳过含 HIGH/CRITICAL 的全部提示），三项缺一不可。**CLI 会话**只依赖前两项配置，由 `defaultMode` 直接进入 bypass，不切会话档位、不传 `--permission-mode` |
| D4 | 安全边界唯一实现 | `permissions.deny` 完整枚举 §3.1 的 7 条；不配置 `ask` 键；不新增 `PreToolUse` hook；不配置 Bash 沙箱。deny 在判定链阶段 1，优先于任何 allow 与任何模式 |
| D5 | 配置作用域 | 只写 user 作用域 `~/.codebuddy/settings.json`。不创建 `<repo>/.codebuddy/settings.json`、不创建 `<repo>/.codebuddy/settings.local.json` |
| D6 | 现有配置合并 | 保留现网 `enabledPlugins` 的 8 个插件开关原样，仅新增 `permissions` 键；禁止整文件覆盖、禁止删改插件开关 |
| D7 | 执行主体（V1.2 回写） | 按用户 2026-11-12「按照方案执行」指令：§4 步骤 1–2（备份 + 合并写入 `~/.codebuddy/settings.json`）由 AI 代执行；步骤 3–4（重启 IDE、复读配置核对、切权限档位）与 §5 的 V1–V4 交互式验收由用户执行，步骤 3 的配置复读由 AI 在重启后的会话内完成。AI 不重启 IDE、不点击 IDE 界面；写入后未重启前不声称配置已生效 |
| D8 | 验收 | §5 的 V1–V6，全部为无破坏性、可观测的核对项；不跑 `npm run build` / `./mvnw test`（无代码改动） |
| D9 | 备份门禁 | 不触发：无新增 / 修改 `db/migration/V*.sql`、不清 `data/h2/`、不覆盖 MySQL、无 schema 变更；不执行 `backup-local-data.sh`（`path-b-data-backup-gate.mdc` 豁免） |
| D10 | 非目标 | §7 全量列举；含「不新增 rule / 不跑 `link-ide-rules.sh`」「不 commit / push」「不改 `docs/运维文档/`」 |

---

## 2. 现网基线（2026-11-12 实测）

| 项 | 事实 |
|---|---|
| 用户级配置 | `~/.codebuddy/settings.json` 已存在，当前**仅**含 `enabledPlugins` 8 项：`pptx` / `pdf` / `docx` / `xlsx` / `agent-browser` / `playwright-cli` / `skills-sec-audit` / `find-skills`（均为 `@codebuddy-plugins-official`、值 `true`）。同目录无 `settings.local.json` |
| 仓库 IDE 目录 | `<repo>/.codebuddy/` 下只有 `rules/`（`.codebuddy/rules/<name>/RULE.mdc` 文件级软链接 → `.cursor/rules/<name>.mdc`），无 `settings*.json` |
| Git 忽略 | `.gitignore` 第 36 行 `.codebuddy/` 整目录忽略；第 11 行 `data/` 整目录忽略；第 31 行 `.trae/` 忽略（按 `code-conventions.mdc` §1.2 为目录级软链接） |
| 现有规则 | `.cursor/rules/` 共 14 个 `.mdc`，与本方案相关的是 `code-conventions.mdc`、`feature-dev-workflow.mdc`、`protect-data-directory.mdc`、`path-b-no-direct-delete.mdc`、`path-b-data-backup-gate.mdc` |
| 数据硬约束 | `protect-data-directory.mdc` 禁止裸删 `data/`、`data/files/`、`data/backups/`；`path-b-no-direct-delete.mdc` 规定 H2 清库唯一入口 `./scripts/path-b/reset-h2.sh --with-files --label before-reset`；`feature-dev-workflow.mdc` §4 规定未明确要求不 commit / push |
| 官方判定链 | `deny`（阶段 1，最高）→ 可信 `allow`（阶段 2，可越过交互态危险命令检查）→ 命令安全检查（阶段 3）→ `ask`（阶段 4）→ bypass 短路（阶段 5）→ 不可信 allow（阶段 6）→ 模式基线（阶段 7）。`deny` 从所有作用域合并，任一作用域命中即拒 |
| 官方 settings 层级 | `flagSettings / cliArg / session > userSettings > policySettings > projectSettings > localSettings > command/sandbox`。会话模式与 CLI `--permission-mode` 均压过 `permissions.defaultMode`；`defaultMode: "auto"` 只认 user / CLI 注入的 settings |
| IDE 模式 | 官方列 `work`（IDE 客户端传入；Read 放行、Edit 询问、Bash 仅安全命令放行）与 `fullAccess`（IDE 客户端传入；跳过全部权限提示，含 HIGH/CRITICAL 危险命令；显式 `deny` 仍最高优先） |
| 规则语法 | `Tool` 或 `Tool(specifier)`；`Bash(git:*)` 匹配所有 `git` 前缀命令；含 `*` 按 bash glob 且 `*` 跨 `/`；`Edit(/.codebuddy/**)` 从项目根匹配；复合命令下 deny 任一子命令命中即触发，allow 要求全部子命令命中 |
| 官方 CI 范例 | 权限规则文档「CI / 流水线场景」即为 `defaultMode: "bypassPermissions"` + 强 `deny`，与本方案 §3.1 同构 |

> 结论：现网不存在与方案冲突的配置；唯一需要保护的存量是 `enabledPlugins` 8 项（D6）。

---

## 3. 唯一配置路径

### 3.1 `~/.codebuddy/settings.json` 目标内容（完整、写死）

```json
{
  "enabledPlugins": {
    "pptx@codebuddy-plugins-official": true,
    "pdf@codebuddy-plugins-official": true,
    "docx@codebuddy-plugins-official": true,
    "xlsx@codebuddy-plugins-official": true,
    "agent-browser@codebuddy-plugins-official": true,
    "playwright-cli@codebuddy-plugins-official": true,
    "skills-sec-audit@codebuddy-plugins-official": true,
    "find-skills@codebuddy-plugins-official": true
  },
  "permissions": {
    "defaultMode": "bypassPermissions",
    "allow": ["Bash"],
    "deny": [
      "Bash(rm:*)",
      "Bash(sudo:*)",
      "Bash(git push:*)",
      "Bash(git reset --hard:*)",
      "Bash(git clean:*)",
      "Edit(.git/**)",
      "Edit(/.codebuddy/**)"
    ]
  }
}
```

`deny` 7 条完整枚举，逐条对应现网约束：

| 规则 | 保护对象 | 依据 |
|---|---|---|
| `Bash(rm:*)` | 覆盖对 `data/`、`data/files/`、`data/backups/`、`data/h2/` 的递归删除与批量删除（含带 `-rf` 与不带 `-rf` 的形式） | `protect-data-directory.mdc`、`path-b-no-direct-delete.mdc`；采用整体前缀而非只拦单一参数组合，避免漏掉不带 `-rf` 的批量删除。按这两条规则，本文不复述被禁止的删库命令字面 |
| `Bash(sudo:*)` | 提权执行 | 官方 CI 范例同款；本机非沙箱 |
| `Bash(git push:*)` | 未授权的远端外发 | `feature-dev-workflow.mdc` §4「未明确要求不 push」 |
| `Bash(git reset --hard:*)` | 未提交改动被清空且不可恢复 | 本地业务数据与工作区改动保护 |
| `Bash(git clean:*)` | 未跟踪文件（含 `data/` 之外的本地产物）被批量清掉 | 同上 |
| `Edit(.git/**)` | Git 元数据 | 官方规则示例 |
| `Edit(/.codebuddy/**)` | 仓库内 IDE 软链接治理结构 | `code-conventions.mdc` §1.2「两个 IDE 目录只做软链接引用，不存第二份正文」 |

不写 `ask` 键：任一 `ask` 规则都会强制弹审批框，与目标互斥。不写 `permissions.disableBypassPermissionsMode`：该字段任一作用域为 `"disable"` 会把 bypass 退回 `default`。`Bash(git commit:*)` 不列入 deny：本地提交可回滚，且由 `feature-dev-workflow.mdc` §4 约束 AI 不主动提交；列入会在用户明确要求提交时制造配置摩擦。

`allow: ["Bash"]` 是 user 作用域可信 allow，位于判定链阶段 2，越过阶段 3 的交互态危险命令检查；它不是对 `deny` 的覆盖（阶段 1 先执行），因此 7 条 deny 仍全部生效。

### 3.2 IDE 会话档位（唯一入口）

**唯一入口**：点击对话输入框下方的锁形权限按钮，在档位列表中选择最宽松的一档；界面文案随客户端版本变化，一律以「列表中最宽松一档」为准，切换后输入框下方出现 `⏵⏵ bypass permissions on` 一类标识即为生效。CLI 会话不做任何档位切换，由 §3.1 的 `defaultMode` 直接进入 bypass；不按 `Shift+Tab`、不传 `--permission-mode`，避免与 user settings 形成两条并行的模式来源。

IDE 客户端传入的会话模式（`work` / `fullAccess`）优先于用户级 `defaultMode`（与 `session > userSettings`、`--permission-mode` 压过 `defaultMode` 的官方规则一致）：IDE 默认 `work` 模式仍会对 Edit 与非安全 Bash 弹确认，因此**必须**显式切档，只改 §3.1 的配置文件不足以让 IDE 免确认。

### 3.3 判定链推演（为什么两边都不弹窗）

| 动作 | 命中阶段 | 结果 |
|---|---|---|
| 读写仓库内文件、`pwd`、`ls`、`npm run build`、`./mvnw test` | IDE：fullAccess 跳过提示；CLI：bypass 阶段 5 短路 | 直接执行，无确认 |
| 被判为危险的 Bash 命令（非 deny 项） | user 级可信 allow 阶段 2，越过阶段 3 | 直接执行，无确认 |
| 对 `data/` 的裸删命令、`git push`、`sudo ...`、`git reset --hard` | deny 阶段 1 | 直接拒绝，不弹窗（不找用户确认，但也不放行） |
| `AskUserQuestion`、`ExitPlanMode` | 交互型工具本身要求交互 | 仍会弹（§6 例外） |
| `rm -rf /` / `rm -rf ~` 等灾难性命令 | deny 阶段 1（命中 `Bash(rm:*)`） | 直接拒绝、不弹窗；官方所述「灾难性命令强制询问」适用于未配置该 deny 的场景，本方案不会走到 |

---

## 4. 实施顺序（写死 4 步）

> 执行主体（V1.2）：步骤 1–2 由 AI 按用户 2026-11-12 指令代执行；步骤 3–4 由用户执行，步骤 3 的配置复读由 AI 在重启后的会话内完成。AI 不重启 IDE、不切档位。

1. 备份现网配置：创建 `~/.codebuddy/settings.json.bak-20261112`，与改动前 `settings.json` 逐字节一致（等价于 `cp ~/.codebuddy/settings.json ~/.codebuddy/settings.json.bak-20261112`；V1.1 由 AI 用等价文件写入执行，判据见 V1）。备份文件名固定，CodeBuddy 只读取 `settings.json` / `settings.local.json`，备份文件不会被当作配置加载；**若该备份文件已存在则停止执行、不得覆盖**，需重跑时先按 §4.1 回滚再开始。
2. 把 §3.1 的完整 JSON 写入 `~/.codebuddy/settings.json`：`enabledPlugins` 8 项保持原值，只新增 `permissions`；校验 JSON 语法。
3. 重启 CodeBuddy IDE（让 user settings 重新加载）并新开会话；由 AI 复读 `~/.codebuddy/settings.json`，与 §3.1 的 JSON 逐行核对（`enabledPlugins` 8 项原样、`defaultMode`、`allow`、7 条 `deny` 完整、无 `ask` 键、无 `disableBypassPermissionsMode`），确认客户端未回写。**核对不通过时的唯一处理**：停在步骤 3，清除相应作用域中的 `ask` 规则或 `disableBypassPermissionsMode: "disable"` 字段（其它作用域干扰的背景见 §6），重启后重新执行步骤 3 的配置复读；未清除前不得进入步骤 4，也不得通过放宽 §3.1 的 `deny` 或改档位来绕过。
4. 按 §3.2 把该会话切到最宽松档，随后按 §5 逐项验收。

不需要执行 `./scripts/common/link-ide-rules.sh`：本方案不新增 `.mdc`、不新建 `.codebuddy/rules/<name>/` 目录与链接。

### 4.1 回滚（唯一路径）

`cp ~/.codebuddy/settings.json.bak-20261112 ~/.codebuddy/settings.json` → 重启 CodeBuddy IDE → 新开会话并按 §3.2 同一入口把权限档位切回默认档。回滚即回到 §2 现网基线；不保留 `permissions` 的部分字段，不留逐条删规则的中间态。

---

## 5. 验收标准（V1–V6，全部无破坏性）

| 编号 | 验收项 | 通过判据 |
|---|---|---|
| V1 | 配置合并 | 复读 `~/.codebuddy/settings.json`，与 §3.1 的 JSON 逐行一致（实施阶段 AI 已静态核对通过：8 项插件原样、`defaultMode = bypassPermissions`、`allow = ["Bash"]`、7 条 `deny`、无 `ask` 键）；重启后再复读一次确认客户端未回写；`~/.codebuddy/settings.json.bak-20261112` 存在且与改动前文件逐字节一致 |
| V2 | 免确认（读 + 写 + 命令） | 新会话中让 AI 执行 `pwd`、`touch /tmp/codebuddy-permission-probe.txt`、`./mvnw -v`，三条均直接执行、无审批弹窗；输入框下方显示最宽松档标识。探针文件保留在 `/tmp` 不清理（§3.1 的 `Bash(rm:*)` 已禁止 `rm`，该临时文件无副作用） |
| V3 | deny 生效且不用确认 | 让 AI 执行 `rm -rf /tmp/codebuddy-permission-probe-nonexistent`（路径不存在，规则失效也无破坏）→ 被直接拒绝且无确认弹窗 |
| V4 | 外发拦截 | 让 AI 执行 `git push --dry-run` → 被直接拒绝且无确认弹窗；`git status` 正常执行 |
| V5 | 仓库零改动 | 实施前基线（2026-11-12 实测）为 2 个未跟踪文件：本方案文档与 `2026-11-12-阶段3AI网关配置项声明化整改技术方案.md`（与本方案无关）；实施后 `git status --short` 输出与基线逐行一致，不产生任何新的未跟踪 / 已修改项；`data/`、`data/h2/`、`data/files/` 条目与实施前一致 |
| V6 | 门禁豁免 | 未执行 `backup-local-data.sh`、未执行 `reset-h2.sh`、未执行 `npm run build` / `./mvnw test`；无 Flyway 迁移、无清库 |

V1–V6 全部通过即为验收完成；任何一项不通过，按 §6 例外清单定位，不通过放宽 `deny` 来换取通过。

---

## 6. 风险与例外

| 项 | 说明 |
|---|---|
| 仍会弹窗 | 仅 `AskUserQuestion`、`ExitPlanMode`（Plan 模式计划审批）等交互型工具本身要求交互，任何模式都会弹。`rm -rf /` / `rm -rf ~` 等灾难性命令已由 §3.1 `Bash(rm:*)` 在阶段 1 直接拒绝、不弹窗；官方「灾难性命令强制询问」适用于未配置该 deny 的场景 |
| 其它作用域干扰 | 若 `flagSettings / cliArg / session / policySettings` 任一存在 `ask` 规则或 `disableBypassPermissionsMode: "disable"`，bypass 会退化；唯一处理见 §4 步骤 3：停在步骤 3 清除对应字段并重新核对，未清除前不进入步骤 4 |
| 受保护文件 | `.git`、`.zshrc`、`.npmrc`、`.codebuddy` 等关键路径在 bypass 下大部分会通过，本方案依赖 §3.1 两条 `Edit` deny 兜住仓库内元数据与规则目录 |
| deny 不是 OS 级沙箱 | `Edit(/.codebuddy/**)` 只拦 Edit / Write / NotebookEdit，拦不住 `python -c 'open(...)'` 这类 Bash 间接写入；本方案不配 Bash 沙箱（§7 非目标），该残余风险由工作流规则约束 AI 行为 |
| 档位文案差异 | 客户端版本不同导致最宽松档名称不同，以「列表中最宽松一档」+ 输入框下方 `bypass permissions on` 标识为准 |
| 上述例外的处理 | 属产品内置行为，不通过改 `permissions` 规避；如需在 Plan 模式下也完全不中断，改用 Craft / Agent 模式（不属于本方案配置范围） |

---

## 7. 非目标与备份门禁

**非目标（全量）**：不改 `backend/` / `frontend/` / `db/migration/` / `src/test/` 任何文件；不新增或修改 `.cursor/rules/*.mdc`、不跑 `link-ide-rules.sh`；不创建项目级 `.codebuddy/settings*.json`；不改 `docs/运维文档/`、`README.md`、`AGENTS.md`；不启用 `CODEBUDDY_IS_SANDBOX=1`、`-y`、`--dangerously-skip-permissions`、`PreToolUse` hook、Bash 沙箱；不执行清库、H2 重置、MySQL 覆盖、删除 `data/` 任何内容；不 commit / push；AI 不重启 IDE、不点击 IDE 界面、不代为操作 IDE 权限面板；§4 步骤 1–2 的备份与写入已按用户 2026-11-12 指令由 AI 执行。

**备份门禁**：按 `path-b-data-backup-gate.mdc`，本方案无 Flyway 迁移、无 `data/h2/` 清理、无 MySQL 覆盖、无 schema / 组织大重构，命中豁免，不执行 `./scripts/common/backup-local-data.sh`。

---

## 8. 附录：已否决方案

| 编号 | 否决项 | 否决原因 |
|---|---|---|
| A | 裸 `fullAccess` / 只设 `defaultMode` 不设 `deny` | 与 `protect-data-directory.mdc`、`path-b-no-direct-delete.mdc`、`feature-dev-workflow.mdc` §4 冲突；本机有真实 MySQL 业务数据、`data/files/` 上传文件与 `data/backups/`，误删不可恢复 |
| B | `defaultMode: "auto"` | 逐动作交给审查员判定、拒绝 3 次即中断本轮，本质仍会打断；且项目级 settings 中的 `auto` 会被忽略回退 `default` |
| C | `acceptEdits` + `allow` 命令白名单 | 只放行 Edit 系列，Bash 仍逐次询问；白名单枚举也无法覆盖日常命令全集 |
| D | 项目级 `<repo>/.codebuddy/settings.json` 提交共享 deny | `.gitignore` 第 36 行整目录忽略，无法入 git；`code-conventions.mdc` §1.2 规定该目录只放 rules 软链接；未显式信任的项目 allow 会被归为不可信层 |
| E | `CODEBUDDY_IS_SANDBOX=1` + `-y` 真 full pass | 官方明确标注高危、仅限隔离容器 / 无外网环境；本机为日常开发环境且可访问真实数据库与凭据 |
| F | `dontAsk` | 语义是「未预批准动作直接拒绝」，与「放行」相反；`AskUserQuestion` / `ExitPlanMode` 也被拒绝 |
| G | `PreToolUse` hook 做硬边界 | 需新增脚本与长期维护；`permissions.deny` 已覆盖且能在 `/permissions` 面板统一审计，官方亦建议优先用 deny |
| H | 只改 `~/.codebuddy/settings.json`、不切 IDE 档位 | IDE 客户端传入的 `work` 模式优先于 `defaultMode`，Edit 与非安全 Bash 仍会弹确认，无法达成目标 |

---

## 9. 修订记录

| 轮次 | 日期 | 本轮新发现问题点 | 处理 | 结论 |
|---|---|---|---|---|
| 初稿 | 2026-11-12 | — | 建立配置规格、现网基线、验收与否决项 | 待方案模式自查 |
| 方案自查第 1 轮 | 2026-11-12 | K=6（4 阻塞）：① §3.2 切换入口写成「锁形按钮 + Shift+Tab 亦可」的并列路径，违反入口唯一；② D3「三者缺一不可」把 IDE 专属 `fullAccess` 施加到 CLI 会话；③ §4 / §6 对 `ask` 与 `disableBypassPermissionsMode` 干扰只写现象、未写死处理动作；④ §3.1 deny 表与 §3.3 判定链把裸删 `data/` 命令字面写进文档，违反 `path-b-no-direct-delete.mdc`；⑤ 全文缺回滚路径；⑥ V2 探针文件清理口径缺失（与 `Bash(rm:*)` 冲突） | 改写 D3、§3.1、§3.2、§3.3、§6；§4 步骤 3 补唯一处理并新增 §4.1 回滚；V2 补探针文件保留口径 | 未收敛（K>2，进入第 2 轮） |
| 方案自查第 2 轮 | 2026-11-12 | K=2，均为非阻塞：① D2「不设置 `disableBypassPermissionsMode`」与 §4 步骤 3「清除已有该字段」措辞落差；② V5「`git status --short` 只显示 1 个文件」在工作区已有其它改动时判据不严谨 | D2 改为「不新增该字段；已有则按 §4 步骤 3 清除」；V5 改为实施前后 `git status --short` 相对比较 | **已收敛**（K≤2 且均为非阻塞并已入修订记录；文首改「已定稿」） |
| 实现自查第 1 轮（执行阶段回写） | 2026-11-12 | K=2（2 阻塞）：① D7 / §7 写死「AI 不写入用户配置」，与用户「按照方案执行」新指令冲突；② V5「新增项仅本方案文档 1 个文件」与实施前 `git status --short` 基线（2 个未跟踪文件）不符 | 文首改 V1.1；D7 / §4 执行主体 / §7 回写；V5 改为实施前后输出逐行一致并记入实测基线。技术决策（§3.1 JSON、7 条 deny、唯一入口）零改动 | 未收敛（含阻塞项，进入第 2 轮） |
| 实现自查第 2 轮 | 2026-11-12 | K=2，均为非阻塞：① §1 编码门禁写「不进入 §2.1 实现自查」，与 V1.1 执行阶段实际做实现自查不符；② §4 步骤 1 写死 `cp`，AI 代执行用的是等价文件写入，方案未标明等价判据 | 回写 §1 门禁口径；§4 步骤 1 补「等价文件写入 + V1 判据」。技术决策与 §3.1 JSON 零改动 | **已收敛**（K≤2 且均为非阻塞并已入修订记录） |
| 实现自查第 3 轮 | 2026-11-12 | K=4（2 阻塞）：① §4 步骤 3 / V1 依赖 IDE 会话 `/permissions` 面板，官方仅记载其为 CLI 斜杠命令，IDE 侧无依据 → 验收不可测（阻塞）；② §3.3 / §6 把 `rm -rf /` 列为「仍会弹」，与 §3.1 `Bash(rm:*)` 在阶段 1 直接拒绝自相矛盾（阻塞）；③ 备份文件比改动前多一个末尾换行，不满足 V1「逐字节一致」（非阻塞）；④ 备份名固定、重复执行会覆盖原始快照（非阻塞） | 修备份文件末尾换行；V1 / §4 步骤 3 改为可测的配置复读；§3.3 / §6 判定链口径改一致；§4 步骤 1 补幂等保护并消除与 §6 的循环引用；D7 / §7 / 文首同步回写为 V1.2。§3.1 JSON 与 7 条 `deny` 零改动 | 未收敛（K>2，进入第 4 轮） |
| 实现自查第 4 轮 | 2026-11-12 | K=1，非阻塞：文首 V1.2 的回写范围描述未覆盖 §4 步骤 1 新增的备份幂等保护 | 文首版本行与状态行补「备份幂等」；复核 D1–D10 逐行落地、配置 diff 仅 13 行新增且无删除行、备份与改动前逐字节一致、lint 0 错误 | **已收敛**（K≤2 且均为非阻塞并已入修订记录） |
| 提交前核对（实现自查第 5 轮） | 2026-11-12 | K=1，非阻塞：V5 记录的另一个未跟踪文件名被误读（写成不存在的「阶段3AI课件配置功能调研与修复技术方案.md」），现网实为「阶段3AI网关配置项声明化整改技术方案.md」 | V5 改正文件名；用户 2026-11-12 后续单独下达「提交并推送」指令，属独立交付动作，不改变本方案技术决策与 §3.1 JSON；该文件与本方案无关，不纳入本次提交 | 已收敛（K≤2 且均为非阻塞并已入修订记录） |
