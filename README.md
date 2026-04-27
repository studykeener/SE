# 后台管理子系统

## 1. 技术栈
- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA (Hibernate)
- MySQL 8
- 单页前端：`src/main/resources/static/index.html`

---

## 2. 当前功能概览

### 2.1 管理员与鉴权
- 管理员 JWT 登录（`/api/admin/auth/login`）
- `SUPER_ADMIN / DATA_ADMIN / CONTENT_REVIEWER` 角色菜单控制
- 默认超管账号自动初始化：`admin / 123456`

### 2.2 统一用户管理（主库在本系统）
- 统一用户列表/详情/新增/修改/删除/批量删除
- 条件筛选：用户名、来源（仅 `WEB/APP`）、状态、注册时间范围
- 用户状态管理：启用/禁用（含批量）
- 细粒度权限：评论/上传开关
- 用户行为记录：按用户查询与新增
- 权限变更审计：落库到 `unified_user_permission_audit`

### 2.3 内容审核
- 单条审核、批量审核（通过/拒绝/复审）
- 拒绝与复审采用居中模态弹窗
- 审核统计：日统计、审核员工作量排序与筛选
- 敏感词库：增删改、级别调整、按词语/敏感程度筛选、操作日志
- 自动审核策略：读取/保存、策略操作日志

### 2.4 文物数据管理
- 文物列表与保存
- CSV 导入导出
- 图谱同步状态字段维护

### 2.5 知识图谱管理（通过子系统 API 代理）
- 实体/关系/三元组在线编辑（新增/修改/删除）
- 同步任务触发与查看
- 通过 `integration` 的 `kg` 子系统路由对接，不直连对方数据库

### 2.6 数据备份与恢复
- 手动备份：全量 / 指定表（下拉选表，显示中文名）
- 定时自动备份：可配置 `cron`、开关、保留天数
- 备份文件加密存储（AES）
- 备份记录列表、下载
- 数据恢复（二次确认：`CONFIRM_RESTORE`）
- 过期备份自动清理
- 备份与恢复权限：**仅 `SUPER_ADMIN`**

### 2.7 日志管理（审计增强）
- 操作日志、系统日志、安全日志、登录日志、数据变更日志
- 多维检索：时间范围、操作人、类型、关键字
- CSV 导出（UTF-8 BOM，Excel 中文不乱码）
- 登录失败记录已写入登录日志
- 全局异常、备份任务执行写入系统日志

### 2.8 系统监控看板
- 实时指标：在线用户估算、今日新增用户、今日内容提交量、审核积压等
- 访问趋势：日/周/月（折线图）
- 数据增长：用户/内容/文物增长趋势（折线图）

---

## 3. 数据库准备
在 MySQL 中执行：

```sql
CREATE DATABASE overseas_artifacts DEFAULT CHARACTER SET utf8mb4;
```

> 使用 `spring.jpa.hibernate.ddl-auto=update`，首次启动会自动建表/补字段。

---

## 4. 关键配置（`application.yml`）

### 4.1 数据库
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`

### 4.2 子系统对接

```yaml
integration:
  mode: mock   # mock 或 real
  user-system-base-url: http://localhost:9001
  artifact-system-base-url: http://localhost:9002
  kg-system-base-url: http://localhost:9003
  paths:
    users: /api/v1/users
    artifacts: /api/v1/artifacts
    review-callback: /api/v1/content/review-result
    kg-entities: /api/v1/kg/entities
    kg-relations: /api/v1/kg/relations
    kg-triples: /api/v1/kg/triples
    kg-sync-jobs: /api/v1/kg/sync/jobs
```

### 4.3 备份配置

```yaml
backup:
  directory: backups
  aes-key-base64: <16/24/32字节key的Base64>
```

---

## 5. 启动项目

```bash
mvn spring-boot:run
```

访问：
- 前端：`http://localhost:8080/`

---

## 6. 常用 API（最新）

### 6.1 统一用户
- `GET /api/admin/unified-users`
- `GET /api/admin/unified-users/{id}`
- `POST /api/admin/unified-users`
- `PUT /api/admin/unified-users/{id}`
- `DELETE /api/admin/unified-users/{id}`
- `DELETE /api/admin/unified-users/batch`
- `PATCH /api/admin/unified-users/{id}/status`
- `PATCH /api/admin/unified-users/batch/status`
- `PATCH /api/admin/unified-users/{id}/permissions`
- `GET /api/admin/unified-users/{id}/behaviors`
- `POST /api/admin/unified-users/{id}/behaviors`

### 6.2 备份恢复（仅 SUPER_ADMIN）
- `GET /api/admin/backup/config`
- `PUT /api/admin/backup/config`
- `GET /api/admin/backup/tables`
- `POST /api/admin/backup/manual`
- `GET /api/admin/backup/records`
- `GET /api/admin/backup/records/{id}/download`
- `POST /api/admin/backup/restore/{id}`

### 6.3 日志
- `GET /api/admin/logs`（操作日志）
- `GET /api/admin/logs/system`
- `GET /api/admin/logs/security`
- `GET /api/admin/logs/login`
- `GET /api/admin/logs/data-change`
- `GET /api/admin/logs/export/operation`
- `GET /api/admin/logs/export/system`
- `GET /api/admin/logs/export/security`

### 6.4 监控看板
- `GET /api/admin/dashboard/overview`

### 6.5 子系统代理
- `GET /api/admin/integrations/endpoints`
- `GET /api/admin/integrations/status`
- `POST /api/admin/integrations/proxy/forward`
  - `system` 支持：`user | artifact | kg`
  - `method` 支持：`GET | POST | PUT | PATCH | DELETE`

---

## 7. 说明与已知约束
- 统一用户来源当前限制为：`WEB`、`APP`
- 角色与权限后端能力可用；前端“角色权限管理”页面当前保留入口、内容待二次设计
- 子系统未就绪时可用 `integration.mode=mock` 先演示流程
