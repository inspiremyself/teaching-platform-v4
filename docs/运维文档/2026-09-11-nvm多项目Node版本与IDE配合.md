# nvm：多项目 Node 版本与 IDE 配合

**创建日期**：2026-09-11  
**适用环境**：macOS + zsh + [nvm](https://github.com/nvm-sh/nvm)（Node Version Manager）  
**相关 IDE**：Cursor / VS Code（二者配置方式相同）

---

## 1. 目标

- 本机安装多个 Node 大版本（如 18 / 20 / 22）。
- **每个仓库**固定一个 Node 版本（通过 `.nvmrc`）。
- 终端与 IDE 尽量使用**同一套** Node，避免「终端能跑、ESLint/调试却报版本错」。

---

## 2. 安装与常用命令

```bash
# 安装指定版本
nvm install 20
nvm install 18

# 查看已安装
nvm ls

# 当前 shell 切换版本
nvm use 20

# 未安装时：在当前目录有 .nvmrc 的情况下，安装并选用该版本
nvm install
```

| 场景 | 命令 |
|------|------|
| 新开终端默认版本 | `nvm alias default 20` |
| 仅当前项目 | 项目根 `.nvmrc` + `nvm use`（见下文） |

`default` 只影响**没有** `.nvmrc` 或尚未切换的目录；有 `.nvmrc` 时以项目为准。

---

## 3. 每个项目：`.nvmrc`

在项目根目录（或 monorepo 中前端子目录）创建 `.nvmrc`，内容为版本号，例如：

```text
20.18.0
```

或简写：

```text
20
```

进入项目后：

```bash
cd /path/to/project
nvm use
```

若该版本未安装：

```bash
nvm install   # 读取当前目录 .nvmrc 并安装、选用
```

**建议习惯**：clone 后先 `nvm install`，再 `npm install`。将 `.nvmrc` **提交到 git**，便于团队与 CI 对齐。

若 `package.json` 中有 `engines.node`，`.nvmrc` 应与之一致。

### 本仓库（teaching-platform-v4）

当前仓库**尚未**在根目录或 `frontend/` 提供 `.nvmrc`。本地前端联调可在 `frontend/` 下自行添加 `.nvmrc` 并与团队约定版本；日常命令见根目录 `README.md` 与 [路径 B / 路径 C 运维文档](./2026-09-09-路径C-本地MySQL联调与H2迁移.md)。

```bash
# 示例：在 frontend 目录已 nvm use 之后
cd frontend && npm install && npm run dev
```

---

## 4. 多项目并行开发

- **不同终端窗口/标签**：各自 `cd` 到对应项目根目录，`nvm use`（或依赖下面的自动切换）即可，互不影响。
- **同一版本只需安装一次**：`nvm install` 装过的版本全局复用，多个项目可共用 `20` 或不同 minor。

---

## 5. zsh：进入目录自动 `nvm use`（可选）

在 `~/.zshrc` 中保留 nvm 本体加载：

```bash
export NVM_DIR="$HOME/.nvm"
[ -s "$NVM_DIR/nvm.sh" ] && \. "$NVM_DIR/nvm.sh"
```

追加自动切换（进入含 `.nvmrc` 的目录时对齐版本）：

```bash
autoload -U add-zsh-hook
load-nvmrc() {
  local nvmrc_path="$(nvm_find_nvmrc)"
  if [ -n "$nvmrc_path" ]; then
    local nvmrc_node_version=$(nvm version "$(cat "${nvmrc_path}")")
    if [ "$nvmrc_node_version" = "N/A" ]; then
      nvm install
    elif [ "$nvmrc_node_version" != "$(nvm version)" ]; then
      nvm use
    fi
  fi
}
add-zsh-hook chpwd load-nvmrc
load-nvmrc
```

修改后执行 `source ~/.zshrc` 或重开终端。

---

## 6. IDE 配合：核心概念

nvm 配置在 **shell** 里；Cursor / VS Code 是 **GUI 进程**，从 Dock/Finder 启动时**不一定**带有 nvm 注入的 `PATH`。

因此要区分两类 Node 使用方：

| 使用方 | 通常如何拿到 Node |
|--------|-------------------|
| **集成终端**里的 `node`、`npm run dev` | 读 `~/.zshrc`（login shell）→ 一般有 nvm |
| **语言服务 / ESLint / F5 调试** | 启动 IDE 时的环境 PATH，或工作区 `settings.json` / `launch.json` 写死的路径 |

### 6.1 集成终端自检

在 Cursor 中打开集成终端，执行：

```bash
which node
node -v
echo $NVM_DIR
```

期望：`which node` 指向 `$HOME/.nvm/versions/node/.../bin/node`，版本与项目 `.nvmrc` 一致。

若不对：确认 `~/.zshrc` 已加载 nvm；进项目目录后执行 `nvm use`；或启用上文 §5 的 `chpwd` hook。

### 6.2 推荐：从已切换版本的终端启动 IDE

```bash
cd /path/to/project
nvm use          # 或依赖 .nvmrc + hook
cursor .         # 若 CLI 未装，可用：open -a Cursor .
```

IDE 会继承当前 shell 的 `PATH`，扩展与调试更容易与项目 Node 一致。

### 6.3 工作区固定 Node 路径（可选，可提交 `.vscode/settings.json`）

将 `v20.18.0` 换成你本机 `nvm ls` 中的实际目录名：

```json
{
  "terminal.integrated.defaultProfile.osx": "zsh",
  "eslint.runtime": "${env:HOME}/.nvm/versions/node/v20.18.0/bin/node",
  "debug.javascript.defaultRuntimeExecutable": "${env:HOME}/.nvm/versions/node/v20.18.0/bin/node"
}
```

说明：路径随 nvm 安装的 patch 版本变化；换机需改版本号，或与团队约定统一 `nvm install` 的版本。

### 6.4 调试：`launch.json` 示例

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "node",
      "request": "launch",
      "name": "Launch Program",
      "program": "${workspaceFolder}/path/to/entry.js",
      "runtimeExecutable": "${env:HOME}/.nvm/versions/node/v20.18.0/bin/node"
    }
  ]
}
```

也可在终端用 `npm run` / 任务跑脚本（任务多在集成终端执行，已带 nvm），调试采用 **attach** 方式，减少路径硬编码。

### 6.5 扩展：按工作区切换 Node

Marketplace 中可选用名称含 **nvm** / **node version** 的扩展，在打开文件夹时根据 `.nvmrc` 调整 Node 相关设置。适合多项目、不习惯从终端启动 IDE 的场景。

### 6.6 macOS：从图标打开 IDE 时终端有 nvm、IDE 没有

常见原因：GUI 应用不读 `~/.zshrc`。

处理方式（任选或组合）：

1. 使用 §6.2：在终端 `nvm use` 后 `cursor .` 打开项目。
2. 在 `~/.zprofile` 中为 login 环境加载 nvm（与 `~/.zshrc` 策略需自行统一，避免重复加载）。
3. 工作区 §6.3 / §6.4 显式指定 `runtimeExecutable`。

---

## 7. 多项目 + IDE 协作习惯（小结）

```text
项目 A/.nvmrc → 20
项目 B/.nvmrc → 18
```

| 场景 | 建议 |
|------|------|
| 日常写码、`npm run dev` | 集成终端 + `.nvmrc` + §5 自动 `nvm use` |
| ESLint/TS 与终端版本不一致 | `eslint.runtime` 或从终端启动 Cursor（§6.2） |
| F5 调试版本不对 | `debug.javascript.defaultRuntimeExecutable` 或 `launch.json` 的 `runtimeExecutable` |
| 多窗口多项目 | 每窗口打开一个仓库根；在该窗口终端 `cd` 到对应根目录 |

---

## 8. 常见问题

| 现象 | 处理 |
|------|------|
| `nvm: command not found` | 检查 `~/.zshrc` 是否 `source` nvm；`source ~/.zshrc` 或重开终端 |
| 终端 OK，Problems 里 ESLint 报 Node 版本 | §6.3 `eslint.runtime` 或 §6.2 从终端启动 IDE |
| 调试不是项目 Node | §6.4 `launch.json` 或 §6.3 默认 runtime |
| 进目录版本未变 | 确认存在 `.nvmrc`；执行 `nvm use`；或启用 §5 hook |
| 与 fnm / volta 并存 | 团队统一一种工具；思路相同（目录级版本文件 + IDE 路径） |

---

## 9. 参考

- nvm 官方仓库：<https://github.com/nvm-sh/nvm>
- 本仓库本地联调索引：[运维文档 README](./README.md)
