# 后台管理子系统（子系统 5）

海外藏中国文物平台的后台管理服务：用户与权限、内容审核、文物数据、知识图谱、备份恢复、日志审计与监控看板。

## 1. 技术栈

| 组件 | 说明 |
|------|------|
| Java 17 | |
| Spring Boot 3.3 | Web、Security、JPA |
| Spring Security + JWT | 管理员鉴权 |
| MySQL 8 | 业务数据（共用库 `overseas_artifacts`） |
| Neo4j | 知识图谱（`/api/admin/kg/**` 直连） |
| 前端 | 单页 `src/main/resources/static/index.html` |

---

## 2. 功能概览

### 2.1 管理员与鉴权
- JWT 登录：`POST /api/admin/auth/login`
- 角色：`SUPER_ADMIN` / `DATA_ADMIN` / `CONTENT_REVIEWER`
- 首次启动自动初始化超管：**`admin` / `123456`**

### 2.2 平台用户管理
- 列表/增删改/批量操作，筛选：用户名、来源（WEB/APP）、状态、注册时间
- 启用/禁用、评论/上传权限开关
- **用户行为追溯**：聚合查询共用表 `comment`、`user_upload_photo`、`user_favorite`、`user_like`
- 权限变更审计：`user_permission_audit`

### 2.3 内容审核
- 待审队列直接读 **`comment` + `user_upload_photo`**（不另建待审表）
- 单条/批量审核、敏感词库、自动审核策略、审核统计

### 2.4 文物数据
- CRUD、CSV 导入导出、馆别与图谱同步状态字段

### 2.5 知识图谱
- **直连 Neo4j**（`/api/admin/kg/**`），实体/关系/三元组 CRUD
- 配置见 `application.yml` → `kg.neo4j`

### 2.6 数据备份与恢复
- 手动/定时备份，AES 加密文件，备份记录与下载
- **仅 `SUPER_ADMIN`** 可恢复（二次确认 + 恢复审计 `restore_logs`）

### 2.7 日志与审计
- 操作 / 系统 / 安全 / 登录 / 数据变更五类日志
- 支持筛选与 **CSV 导出**（含登录日志、数据变更日志）

### 2.8 系统监控看板
- 在线用户、今日新增用户、今日内容提交、审核积压
- 访问量趋势（按 WEB/APP **日登录人数**，下拉切换子系统）
- 数据增长趋势（用户 / 内容 / 文物，近 14 天）

---

## 3. 首次部署（队友拉代码后）

> **不会自动建表**：`spring.jpa.hibernate.ddl-auto=none`，必须手动执行 SQL。

### 3.1 环境要求
- JDK 17、Maven 3.8+
- MySQL 8（库名 `overseas_artifacts`）
- Neo4j（可选，仅知识图谱功能需要）

### 3.2 初始化 MySQL（全新库）

```bash
# 1. 创建空库（或在 mysql 客户端执行 CREATE DATABASE）
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS overseas_artifacts DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 建全部表（7 张共用表 + 15 张子系统5 表 = 22 张）
mysql -u root -p overseas_artifacts < docs/schema-full.sql
```

表结构说明见 [`docs/database-schema-subsystem5.md`](docs/database-schema-subsystem5.md)。

### 3.3 从旧库升级（可选）

| 脚本 | 用途 |
|------|------|
| `docs/migration-login-source-system.sql` | `login_logs` 增加非空字段 `source_system` |
| `docs/migration-drop-unused-tables.sql` | 删除冗余表 `review_contents`、`user_behaviors` |

全新环境只跑 `schema-full.sql` 即可，**不必**跑迁移脚本。

### 3.4 修改配置

编辑 `src/main/resources/application.yml`：

- `spring.datasource.*`：MySQL 地址与账号
- `kg.neo4j.*`：Neo4j 连接（不用 KG 可设 `kg.neo4j.enabled: false`）
- `integration.inbound-api-key`：队友调用 `/api/integration/**` 时的密钥
- `backup.aes-key-base64`：生产环境请更换

### 3.5 启动

```bash
mvn spring-boot:run
```

浏览器访问：**http://localhost:8080/** ，使用 `admin / 123456` 登录。

---

## 4. 与 Web/App 子系统对接

本系统与队友共用 **同一 MySQL**，多数数据 **直写共用表**，无需 HTTP 代理。

### 4.1 共用表（7 张，全组维护）

`user`、`artifact`、`comment`、`user_favorite`、`user_like`、`user_upload_photo`，以及旧版兼容表 `admin_user`。

### 4.2 登录与看板统计

Web/App 用户登录成功后 **INSERT `login_logs`**：

```sql
INSERT INTO login_logs (user_type, user_id, username, result, ip_address, source_system, login_time)
VALUES ('USER', ?, ?, 'SUCCESS', ?, 'web', NOW());  -- App 端 source_system 写 'app'
```

- `source_system` **必填**：`web` 或 `app`（后台管理员登录由本子系统写 `admin`）
- 建议同时更新：`UPDATE user SET last_login_at=NOW(), last_login_ip=? WHERE user_id=?`

### 4.3 可选 HTTP 入站接口

队友也可通过 HTTP 提交评论/照片/登录（请求头 `X-Integration-Api-Key`）：

- `POST /api/integration/comments`
- `POST /api/integration/photos`
- `POST /api/integration/logins`
- `GET /api/integration/health`

直写数据库与 HTTP 上报 **二选一即可**，不要重复写入。

---

## 5. 数据库表清单（22 张 MySQL）

**7 张共用**：`admin_user`、`artifact`、`user`、`comment`、`user_favorite`、`user_like`、`user_upload_photo`

**15 张子系统5 新增**：`admin_users`、`role_definitions`、`permission_definitions`、`role_permission_assignments`、`admin_role_permission_audit`、`user_permission_audit`、`sensitive_words`、`review_strategy_config`、`backup_records`、`backup_task_config`、`restore_logs`、`operation_logs`、`login_logs`、`system_logs`、`data_change_logs`

知识图谱数据在 **Neo4j**，不在 MySQL。

---

## 6. 常用 API

### 鉴权
- `POST /api/admin/auth/login`
- `GET /api/admin/auth/me`

### 用户
- `GET/POST/PUT/DELETE /api/admin/unified-users`
- `GET /api/admin/unified-users/{id}/behaviors` — 行为追溯（读共用表）
- `GET /api/admin/unified-users/{id}/permission-audit`

### 审核
- `GET /api/admin/reviews` — 查询参数 `sourceTable=comment|user_upload_photo`
- `POST /api/admin/reviews/{sourceTable}/{id}/review`

### 文物 / 知识图谱
- `/api/admin/artifacts/**`
- `/api/admin/kg/**`

### 备份（恢复需 SUPER_ADMIN）
- `GET/PUT /api/admin/backup/config`
- `POST /api/admin/backup/manual`
- `POST /api/admin/backup/restore/{id}`
- `GET /api/admin/backup/restore-logs`

### 日志（均可筛选；带 `/export/` 的可导出 CSV）
- `GET /api/admin/logs`、`/system`、`/security`、`/login`、`/data-change`
- `GET /api/admin/logs/export/{operation|system|security|login|data-change}`

### 看板
- `GET /api/admin/dashboard/overview`

### RBAC
- `/api/admin/rbac/**`、`/api/admin/users/**`

---

## 7. 项目文档

| 文件 | 说明 |
|------|------|
| [`docs/schema-full.sql`](docs/schema-full.sql) | 本地/演示环境全量建表 |
| [`docs/schema-subsystem5-addon.sql`](docs/schema-subsystem5-addon.sql) | 在已有 7 张共用表上仅追加子系统5 表 |
| [`docs/database-schema-subsystem5.md`](docs/database-schema-subsystem5.md) | 表设计说明 |

---

## 8. 说明与约束

- JPA **不会**自动建表/改表；表结构以 `docs/schema-full.sql` 为准
- 审核队列、行为追溯均使用 **7 张共用表**，不依赖 `review_contents`、`user_behaviors`（已废弃）
- 在线用户、访问量统计依赖队友写入 `login_logs`（`user_type=USER`，`source_system=web|app`）
- 生产环境请修改默认密码、JWT 密钥、备份 AES 密钥、`inbound-api-key`
