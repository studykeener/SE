# 后台管理子系统（子系统 5）

海外藏中国文物知识管理与服务平台的后台管理：管理员鉴权与 RBAC、平台用户管理、内容审核、文物数据、知识图谱、备份恢复、日志审计、系统监控看板。

技术栈：**Java 17 · Spring Boot 3.3 · Spring Security + JWT · MySQL 8 · Neo4j（可选）· 单页管理前端**（`src/main/resources/static/index.html`）。

---

## 1. 角色与菜单

| 角色 | 编码 | 典型可见菜单 |
|------|------|----------------|
| 超级管理员 | `SUPER_ADMIN` | 全部（含 RBAC、管理员、备份恢复等） |
| 内容审核员 | `CONTENT_REVIEWER` | **仅「内容审核」**（`REVIEW_VIEW` / `REVIEW_ACTION`） |
| 数据管理员 | `DATA_ADMIN` | 文物数据、知识图谱、系统概览等 |

说明：

- **系统概览（看板）** 需要权限 `STATS_VIEW`；内容审核员**没有**该权限，登录后**不会**出现「系统概览」菜单。
- 首次启动自动创建超管：`admin` / `123456`（`AdminAccountInitializer`）。

---

## 2. 功能模块

### 2.1 管理员与 RBAC

- JWT：`POST /api/admin/auth/login`、`GET /api/admin/auth/me`
- 管理员表：`admin_users`（每人一个角色）
- 角色权限：`/api/admin/rbac/**`，内置三类角色 + 自定义角色

### 2.2 平台用户管理

- 管理共用表 **`user`**（Web 来源 `web`、App 来源 `app`）
- 增删改查、筛选（用户名 / 来源 / 状态 / 注册时间）、批量启用禁用、批量禁止评论/上传
- 用户追溯：聚合 `comment`、`user_upload_photo`、`user_favorite`、`user_like`
- 权限变更审计：`user_permission_audit`
- API：`/api/admin/unified-users/**`

### 2.3 内容审核

- 待审队列直接读 **`comment` + `user_upload_photo`**（不另建待审表）
- 敏感词库、自动审核策略、单条/批量审核、审核统计
- Web/App 可 **直写共用表**（推荐）或调用 `POST /api/integration/comments|photos`
- 对接说明见 [`docs/content-review-handoff/README.md`](docs/content-review-handoff/README.md)

### 2.4 文物数据

- CRUD、CSV 导入导出、按馆别筛选
- 哈佛 / 波士顿缩略图可走爬虫组图片 API（`application.yml` → `artifact.image-api`）

### 2.5 知识图谱

- 直连 Neo4j：`/api/admin/kg/**`（`kg.neo4j.enabled` 可关闭）

### 2.6 备份与恢复

- 手动/定时备份、AES 加密、备份下载
- **仅超级管理员**可恢复（`restore_logs` 审计）

### 2.7 日志

- 操作 / 系统 / 安全 / 登录 / 数据变更
- 筛选与 CSV 导出：`/api/admin/logs/**`

### 2.8 系统监控看板

- 入口：**系统概览**（需 `STATS_VIEW`）
- 指标：在线用户（近 15 分钟）、总用户、今日新增、今日内容提交、待审/待复审、队列积压、文物总数
- 访问量趋势：按 WEB/APP **去重登录人数**（日 7 / 周 8 / 月 6），数据来自 `login_logs`
- 数据增长：近 14 天用户 / 内容 / 文物累计曲线
- API：`GET /api/admin/dashboard/overview`
- **未实现**：异常告警、邮件/短信通知（课程选做）

---

## 3. 环境与启动

### 3.1 要求

- JDK 17、Maven 3.8+
- MySQL 8
- Neo4j 5（仅知识图谱需要）

### 3.2 配置

编辑 `src/main/resources/application.yml`：

| 配置项 | 说明 |
|--------|------|
| `spring.datasource.*` | MySQL；YAML 中密码含 `!` 须加引号 |
| `spring.jpa.hibernate.ddl-auto` | 开发常用 `update`；生产建议 `none` 并以 SQL 脚本为准 |
| `kg.neo4j.*` | 图数据库；不用可 `enabled: false` |
| `integration.inbound-api-key` | 队友调用 `/api/integration/**` |
| `artifact.image-api` | 哈佛/MFA 缩略图代理 |
| `backup.aes-key-base64` | 生产请更换 |
| `image-moderation.mode` | `mock` / `real` / `local`（`local` 需 ONNX，启动较慢） |

### 3.3 初始化数据库

**推荐全量脚本**（6 张共用表 + 15 张子系统 5 表 = **21 张**）：

```bash
mysql -u root -p < docs/schema-6plus15.sql
```

默认库名：`overseas_artifacts`。若使用远程库（如 `overseas_chinese_artifacts`），请先改脚本中的库名或只执行表结构部分。

其他脚本：

| 文件 | 用途 |
|------|------|
| [`docs/schema-full.sql`](docs/schema-full.sql) | 历史全量脚本（22 张，含旧表名） |
| [`docs/schema-subsystem5-addon.sql`](docs/schema-subsystem5-addon.sql) | 已有共用表时仅追加子系统 5 表 |
| [`docs/database-schema-subsystem5.md`](docs/database-schema-subsystem5.md) | 表结构说明 |

### 3.4 启动与访问

```bash
mvn spring-boot:run
```

浏览器：**http://localhost:8080/** ，使用 `admin` / `123456` 登录。

---

## 4. 与 Web / App 对接

共用 **同一 MySQL**。评论、上传、用户等优先 **直写共用表**；也可选 HTTP 集成接口。

### 4.1 共用业务表（6 张）

`artifact`、`user`、`comment`、`user_favorite`、`user_like`、`user_upload_photo`

后台管理员账号在 **`admin_users`**（子系统 5 专用，与前台 `user` 分离）。

### 4.2 登录日志（看板访问统计）

Web/App 用户登录成功后写入 `login_logs`（`user_type=USER`，`result=SUCCESS`，**`source_system` 必填 `web` 或 `app`**）：

```sql
INSERT INTO login_logs (user_type, user_id, username, result, ip_address, source_system, login_time)
VALUES ('USER', ?, ?, 'SUCCESS', ?, 'web', NOW());
```

或：`POST /api/integration/logins`（请求头 `X-Integration-Api-Key`）。

建议同时更新：`user.last_login_at` / `last_login_ip`（集成接口会自动更新）。

### 4.3 集成 API（可选）

| 接口 | 说明 |
|------|------|
| `POST /api/integration/comments` | 提交评论（敏感词 + 自动策略） |
| `POST /api/integration/photos` | 提交上传照片（进人工队列） |
| `POST /api/integration/logins` | 登录上报（看板统计） |
| `GET /api/integration/health` | 健康检查 |

直写库与 HTTP **勿对同一条内容重复写入**。

内容审核直写库方案：[`docs/content-review-handoff/`](docs/content-review-handoff/)

---

## 5. 常用 API 速查

| 模块 | 路径前缀 |
|------|----------|
| 鉴权 | `/api/admin/auth` |
| 管理员 | `/api/admin/users` |
| RBAC | `/api/admin/rbac` |
| 平台用户 | `/api/admin/unified-users` |
| 内容审核 | `/api/admin/reviews` |
| 文物 | `/api/admin/artifacts` |
| 知识图谱 | `/api/admin/kg` |
| 备份 | `/api/admin/backup` |
| 日志 | `/api/admin/logs` |
| 看板 | `/api/admin/dashboard` |
| 集成 | `/api/integration` |

审核查询示例：`GET /api/admin/reviews?status=PENDING`

---

## 6. 项目文档

| 文件 | 说明 |
|------|------|
| [`docs/schema-6plus15.sql`](docs/schema-6plus15.sql) | **推荐** 全量建表（21 张） |
| [`docs/database-schema-subsystem5.md`](docs/database-schema-subsystem5.md) | 表设计说明 |
| [`docs/content-review-handoff/README.md`](docs/content-review-handoff/README.md) | Web/App 内容审核直写库对接包 |
| [`docs/neo4j-graph-db-setup-guide.md`](docs/neo4j-graph-db-setup-guide.md) | Neo4j 部署说明 |

---

## 7. 约束与说明

- 审核队列、用户行为追溯均基于 **6 张共用表**，不依赖已废弃的 `review_contents`、`user_behaviors`。
- 看板访问量依赖队友写入 `login_logs`；无登录日志时趋势图为空属正常。
- 修改 `CommentAuditStatusConverter` 等实体后若看板 500，请 **完整重启** 后端（避免 IDE 热加载 class 不完整）。
- 生产环境请修改：超管密码、JWT 密钥、备份 AES 密钥、`integration.inbound-api-key`。
