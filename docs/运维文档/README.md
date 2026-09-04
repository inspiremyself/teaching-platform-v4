# 运维文档索引

本目录存放本地启动与运维说明；文件名以**创建日期**为前缀。

| 文档 | 说明 | 脚本 |
|------|------|------|
| [2026-09-04-路径A-Docker-Compose整站启动.md](./2026-09-04-路径A-Docker-Compose整站启动.md) | MySQL + 后端 + 前端三容器 | `scripts/path-a/` |
| [2026-09-04-路径B-本地开发H2启动.md](./2026-09-04-路径B-本地开发H2启动.md) | 本地联调；H2 **文件库**持久化 | `scripts/path-b/`、`scripts/common/` |

## 快速示例

```bash
# 路径 A
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh

# 路径 B（两个终端）
./scripts/path-b/backend.sh
./scripts/path-b/frontend.sh
```

日常开发优先路径 B；验收/整站演示用路径 A。二者勿并行占用同一组端口（`8080` / `5173`）。
