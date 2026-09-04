# 路径 A：Docker Compose 整站启动

**创建日期**：2026-09-04  
**适用场景**：本机一次拉起 MySQL + 后端 + 前端三容器，适合演示、验收、接近生产的联调。  
**配套脚本**：`scripts/path-a/up.sh`、`scripts/path-a/down.sh`、`scripts/path-a/logs.sh`、`scripts/common/healthcheck-login.sh`

### 示例用法

在仓库根目录执行：

```bash
# 路径 A
./scripts/path-a/up.sh
./scripts/common/healthcheck-login.sh
./scripts/path-a/down.sh
```

---

## 1. 前置条件

| 依赖 | 说明 |
|------|------|
| Docker | 建议 Docker Desktop / Engine 可用 |
| Docker Compose | `docker compose version` 可执行（Compose V2） |
| 端口空闲 | `3306`（MySQL）、`8080`（后端）、`5173`（前端容器映射到宿主机） |

检查命令：

```bash
docker -v
docker compose version
```

---

## 2. 架构与端口

| 服务 | 容器名 | 宿主机端口 | 说明 |
|------|--------|------------|------|
| MySQL 8.2 | `teaching-platform-mysql` | `3306` | 库名 `teaching_platform`，root 密码 `123456` |
| 后端 | `teaching-platform-backend` | `8080` | Spring profile=`local`，连 Compose 内 MySQL |
| 前端 | `teaching-platform-frontend` | `5173` → 容器 `80` | 静态站点，API 指向 `http://localhost:8080/api/v1` |

访问地址：

- 前端：http://localhost:5173/
- 后端 API：http://localhost:8080/api/v1

---

## 3. 启动步骤

在仓库根目录执行：

```bash
./scripts/path-a/up.sh
```

等价于：

```bash
docker compose up --build -d
```

首次构建会下载镜像并编译前后端，耗时较长；之后重启会快很多。

查看日志：

```bash
./scripts/path-a/logs.sh
# 或只看后端
./scripts/path-a/logs.sh backend
```

---

## 4. 健康检查

后端就绪后，用登录接口探活（需带角色）：

```bash
./scripts/common/healthcheck-login.sh
```

教师登录成功时应返回 `code=0` 且响应中含 token。

手动示例：

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"t9001","password":"123456","role":"TEACHER"}'
```

---

## 5. 演示账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 教师 | `t9001` | `123456` |
| 学生 | `20260001` | `123456` |

演示班级：`SE2026-1`  
前端登录页需选择对应身份（教师 / 学生）。

---

## 6. 停止与清理

停止并移除容器（默认保留 MySQL / 文件存储卷）：

```bash
./scripts/path-a/down.sh
```

若需连同数据卷一并删除（会清空演示库与上传文件）：

```bash
./scripts/path-a/down.sh --volumes
```

---

## 7. 常见问题

1. **端口被占用**  
   先结束占用 `3306/8080/5173` 的进程，或临时改 `docker-compose.yml` 端口映射。

2. **后端一直连不上 MySQL**  
   MySQL 首次初始化较慢。可 `./scripts/path-a/logs.sh mysql` / `backend` 观察，待 MySQL ready 后后端会重试成功；必要时 `./scripts/path-a/up.sh` 再启一次 backend。

3. **前端能开但登录失败**  
   先跑 `./scripts/common/healthcheck-login.sh` 确认后端；确认浏览器访问的是本机 `5173`，且后端 `8080` 可达。

4. **与路径 B 同时跑**  
   不要同时占用同一组端口。路径 B 若已占用 `8080/5173`，请先停掉本地进程再启路径 A。

---

## 8. 相关配置位置

| 文件 | 作用 |
|------|------|
| `docker-compose.yml` | 三服务编排、环境变量、卷 |
| `backend/Dockerfile` | 后端镜像构建 |
| `frontend/Dockerfile` | 前端镜像构建 |
| `backend/src/main/resources/application.yml` | 默认数据源 / JWT / 端口 / `FILE_STORAGE_ROOT` |
| `backend/src/main/resources/application-local.yml` | `local` profile 日志等覆盖 |

容器内上传根为 `/app/data/files`（Compose 卷 `file-storage`）。实验答题图相对路径为 `lab-answers/{yyyy-MM-dd}/…`；课程资料为 `materials/…`（与路径 B 语义一致，仅宿主机落点不同）。
