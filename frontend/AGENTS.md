# frontend workspace knowledge

## OVERVIEW

这里是前端运行边界与页面组织层：Vite 启动、Router 壳层、Pinia 登录态、构建命令、视图分叉都在这一层汇总，而更细的 API/teacher/student 规则继续下沉到子 AGENTS。

## STRUCTURE

```text
frontend/
├─ package.json               # dev / build / preview / test / lint / format
├─ vite.config.ts             # Vite 构建入口（/api 代理）
├─ eslint.config.js           # ESLint 9 flat config
├─ .prettierrc.json           # 格式化约定
├─ vitest.config.ts           # 前端单测
├─ index.html                 # 前端挂载入口
├─ src/main.ts                # Vue bootstrap
├─ src/router/                # 三角色壳层与守卫
├─ src/stores/                # 登录态/权限态（auth.ts）
├─ src/api/                   # 已有子级 AGENTS
├─ src/types/                 # 契约类型 + enums.ts
├─ src/utils/                 # request.ts / authToken.ts
├─ src/composables/           # use<页面名>.ts
├─ src/components/            # question-config/ typed-editor/ lab/
├─ src/layout/                # 壳层 + navigation.ts
├─ src/views/teacher/         # 已有子级 AGENTS
├─ src/views/student/         # 已有子级 AGENTS
└─ src/views/admin/           # 已有子级 AGENTS
```

## WHERE TO LOOK

| 任务 | 位置 | 说明 |
|---|---|---|
| 脚本入口 | `package.json` | `dev` / `build` / `preview` / `test` / `lint` / `format`（见 `toolchain-and-test-conventions.mdc` §1） |
| 构建配置 | `vite.config.ts` | Vite 根配置 |
| 启动挂载 | `src/main.ts` | Pinia、Router、Element Plus |
| 路由与守卫 | `src/router/index.ts` | `/teacher`、`/student`、登录跳转、角色校验 |
| 登录态中心 | `src/stores/auth.ts` | token、user、permissions、homePath |
| API 契约 | `src/api/` | 其下已有 API 层 AGENTS |
| 教师端页面 | `src/views/teacher/` | 其下已有教师页 AGENTS |
| 学生端页面 | `src/views/student/` | 其下已有学生页 AGENTS |
| 全局样式与壳层 | `src/assets/` `src/layout/` | 页面骨架与全局视觉基础 |

## 规范引用（细则不在本文件重复）

| 任务 | 规则 |
|---|---|
| SFC 风格、组件拆分、数据流 | `.cursor/rules/frontend-code-style.mdc` |
| `api/` / `types/` / `request.ts` 契约 | `.cursor/rules/frontend-api-contract.mdc` |
| npm scripts、ESLint / Prettier、CI | `.cursor/rules/toolchain-and-test-conventions.mdc` |
| 总纲与冲突优先级 | `.cursor/rules/code-conventions.mdc` |

下列 CONVENTIONS 只保留前端运行与壳层事实；子目录 AGENTS 负责各角色页面信息架构，规范细则以 `.cursor/rules/` 为准，本层不复制。

## CONVENTIONS

- 前端是三角色壳层：教师端、学生端与校管端在 `router/index.ts` 里分开定义，不要把三者重新揉成单套导航语义。
- 登录态与角色主页统一收敛在 `stores/auth.ts`；路由守卫依赖这里的 `token/user/homePath`。
- `package.json` 现有 6 个脚本：`dev` / `build` / `preview` / `test` / `lint` / `format`；CI 顺序为 `npm ci → lint → test → build`，见 `toolchain-and-test-conventions.mdc` §1。
- 本地联调时，Vite 代理目标现在可通过 `VITE_API_TARGET` 覆盖，不必再假设后端固定跑在 `8080`。
- 若需要接入 H2/test profile 后端，推荐：`cmd /c "set VITE_API_TARGET=http://127.0.0.1:18080 && npm run dev -- --host 127.0.0.1 --port 18081"`。
- 具体 API 契约、教师页 / 学生页 / 校管页信息架构已下沉到子目录 AGENTS 与上表规则；这一层只负责运行与壳层组织，不重复细则。

## ANTI-PATTERNS

- 不要在页面层重新发明登录态管理或绕开 `stores/auth.ts`。
- 不要把 `/teacher` 与 `/student` 路由边界改成混合壳层，除非同步重写权限与导航语义。
- 不要把 `dist/`、`node_modules/`、`.playwright-cli/` 里的产物当作源码依据。
- 不要在 `package.json` 没有约定的情况下臆造本地测试脚本写进文档。

## COMMANDS

```bash
# 推荐：仓库根目录
../scripts/path-b/frontend.sh

npm install
npm run dev
npm run build
npm run preview
VITE_API_TARGET=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 18081
```
