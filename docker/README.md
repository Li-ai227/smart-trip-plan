# 本地运行环境（Docker Compose）

> 对应变更 **C-003 运行环境与基础设施**（AC-1）。仅用于本地开发/联调，非生产编排。

## 组成

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| Nacos | `nacos/nacos-server:v3.2.0` | 8848(http) / 9848(grpc) | 单机模式；A2A 注册逻辑归 C-005，本期仅保证可达 |
| Redis | `redis:7-alpine` | 6379 | 设访问口令（编码规范 §7）；用户数据 24h TTL、不落盘（ADR-006） |

## 准备环境变量

```bash
cp ../.env.example ../.env       # 复制后填入真实值；.env 已在 .gitignore，禁止提交
```

`.env` 可覆盖端口与口令：`NACOS_PORT` / `REDIS_PORT` / `REDIS_PASSWORD` 等。

## 启动 / 健康检查 / 停止

```bash
docker compose -f docker/docker-compose.yml up -d      # 启动
docker compose -f docker/docker-compose.yml ps         # 查看健康状态（healthy）
# 验证：
#   Nacos 控制台   http://localhost:8848/nacos
#   Redis          redis-cli -a "$REDIS_PASSWORD" ping   # 期望 PONG
docker compose -f docker/docker-compose.yml down        # 停止并清理
```

预期 `docker compose up -d` 到两服务健康 < 60s（C-003 NFR 性能）。

## 常见问题

- **端口被占用（8848 / 6379）**：先 `docker compose ps` / `netstat` 排查占用，或在 `.env`
  设置 `NACOS_PORT` / `REDIS_PORT` 改用其他宿主端口后重试。
- **Nacos healthcheck 失败**：部分镜像不含 `curl`，可将 compose 中 healthcheck 改为 `wget`
  或 TCP 探测；应用侧健康检查使用 JDK Socket 探针（`SocketNacosProbe`），不依赖容器内工具。
- **应用连接**：以 `local` profile 启动 `smart-trip-server`，默认连 `127.0.0.1:8848` / `127.0.0.1:6379`；
  其余 profile 经环境变量注入（见 `application-dev.yml` 与 `.env.example`）。

## 与应用健康检查的关系

`smart-trip-server` 的 `/actuator/health` 聚合 `redis`（Spring 原生）与 `nacos`（`NacosHealthIndicator`
轻量连通性探针）两个组件；任一不可用时整体 `DOWN` 并标明失败组件（C-003 AC-5）。
