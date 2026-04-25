# 统一用户主库方案（后台管理子系统）

## 1. 架构约束（对外统一口径）

- 用户主数据放在 `admin-backend`（本系统）数据库。
- 其他子系统（知识服务、掌上博物馆等）**只能通过 API 访问**，禁止跨系统直连数据库。
- 用户状态、细粒度权限、行为记录以本系统为准。
- 所有跨系统调用必须带鉴权（JWT/服务间 token）并记录审计日志。

---

## 2. 数据库表设计（MySQL 8）

> 说明：若已有同名字段，可按现有命名适配；以下为推荐最小可用结构。

```sql
-- 2.1 统一用户主表
CREATE TABLE IF NOT EXISTS unified_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  display_name VARCHAR(128) NULL,
  email VARCHAR(128) NULL,
  phone VARCHAR(32) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',           -- ENABLED / DISABLED
  source_system VARCHAR(32) NOT NULL,                      -- KNOWLEDGE / MUSEUM_APP / WEB / APP ...
  source_user_id VARCHAR(64) NOT NULL,                     -- 子系统原始用户ID
  comment_allowed TINYINT(1) NOT NULL DEFAULT 1,
  upload_allowed TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_source_user (source_system, source_user_id),
  KEY idx_username (username),
  KEY idx_created_at (created_at),
  KEY idx_source_system (source_system),
  KEY idx_status (status)
);

-- 2.2 用户行为记录表（可聚合上报）
CREATE TABLE IF NOT EXISTS unified_user_behaviors (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  behavior_type VARCHAR(32) NOT NULL,                      -- POST / COMMENT / UPLOAD / LOGIN / ...
  behavior_content TEXT NULL,
  source_system VARCHAR(32) NOT NULL,
  source_record_id VARCHAR(64) NULL,
  behavior_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, behavior_time),
  KEY idx_behavior_type (behavior_type),
  CONSTRAINT fk_behavior_user FOREIGN KEY (user_id) REFERENCES unified_users(id)
);

-- 2.3 用户权限变更审计表（细粒度）
CREATE TABLE IF NOT EXISTS unified_user_permission_audit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  operator VARCHAR(64) NOT NULL,                           -- 操作管理员
  old_status VARCHAR(16) NULL,
  new_status VARCHAR(16) NULL,
  old_comment_allowed TINYINT(1) NULL,
  new_comment_allowed TINYINT(1) NULL,
  old_upload_allowed TINYINT(1) NULL,
  new_upload_allowed TINYINT(1) NULL,
  reason VARCHAR(256) NULL,
  operated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_operated (user_id, operated_at),
  CONSTRAINT fk_perm_audit_user FOREIGN KEY (user_id) REFERENCES unified_users(id)
);
```

---

## 3. 统一用户管理 API（由后台管理子系统提供）

统一前缀：`/api/admin/unified-users`

### 3.1 用户列表查询（支持筛选 + 分页）

- `GET /api/admin/unified-users?page=0&size=20&username=&sourceSystem=&status=&createdFrom=&createdTo=`
- 返回示例：

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "content": [
      {
        "id": 101,
        "username": "u_demo",
        "displayName": "示例用户",
        "email": "demo@test.com",
        "status": "ENABLED",
        "sourceSystem": "MUSEUM_APP",
        "sourceUserId": "A10001",
        "commentAllowed": true,
        "uploadAllowed": false,
        "createdAt": "2026-04-25T10:22:00"
      }
    ],
    "number": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### 3.2 新增用户

- `POST /api/admin/unified-users`
- Body：

```json
{
  "username": "new_user",
  "displayName": "新用户",
  "email": "new@xx.com",
  "phone": "13800000000",
  "sourceSystem": "KNOWLEDGE",
  "sourceUserId": "KG_001"
}
```

### 3.3 修改用户基础信息

- `PUT /api/admin/unified-users/{id}`

### 3.4 删除用户

- `DELETE /api/admin/unified-users/{id}`

### 3.5 批量操作

- `PATCH /api/admin/unified-users/batch/status?status=ENABLED&ids=1&ids=2`
- `DELETE /api/admin/unified-users/batch`（Body: `{ "ids":[1,2,3] }`）

### 3.6 细粒度权限更新

- `PATCH /api/admin/unified-users/{id}/permissions`
- Body：

```json
{
  "commentAllowed": false,
  "uploadAllowed": true,
  "reason": "违规评论"
}
```

### 3.7 用户行为记录查询

- `GET /api/admin/unified-users/{id}/behaviors?page=0&size=20&type=&from=&to=`

### 3.8 行为记录上报（供子系统调用）

- `POST /api/admin/unified-users/{id}/behaviors`
- Body：

```json
{
  "behaviorType": "COMMENT",
  "behaviorContent": "发表了评论",
  "sourceSystem": "MUSEUM_APP",
  "sourceRecordId": "CMT_8899",
  "behaviorTime": "2026-04-25T11:00:00"
}
```

---

## 4. 子系统对接说明（可直接发群）

各子系统（知识服务、掌上博物馆）请按以下约束接入：

1. 不再直连后台管理数据库。
2. 用户信息与状态以后台管理系统 API 为准。
3. 涉及用户状态/权限变更时，必须调用统一用户 API。
4. 业务行为（发布、评论、上传等）需实时或准实时上报到行为记录接口。
5. 必须提供 `sourceSystem` 与本系统 `sourceUserId`，用于映射统一用户。

---

## 5. 联调最小清单（MVP）

先打通以下 6 项即可进入验收：

1. 用户列表查询（含用户名、来源、注册时间筛选）
2. 用户启用/禁用
3. 评论/上传权限开关
4. 用户行为记录查询
5. 子系统行为上报
6. 批量状态更新

---

## 6. 风险与建议

- 风险：子系统本地仍保留独立状态字段，可能与主库不一致。  
  建议：业务鉴权时实时查统一用户 API，或订阅状态变更事件同步缓存。

- 风险：行为上报延迟。  
  建议：先同步调用，后续改异步队列（Kafka/RabbitMQ）提升稳定性。

