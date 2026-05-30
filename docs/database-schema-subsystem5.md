# 子系统5（后台管理）数据库表设计终稿

> 与代码实体对齐版本。共用库重建时以本文为准；API 路径 `/api/admin/unified-users` 保留，底层表为 `user`（7 张共用表之一）。

## 表清单（7 + 15 = 22 张）

| 分类 | 表名 | 说明 |
|------|------|------|
| 共用 | `user` | 前台用户主表 |
| 共用 | `comment` | 评论（含审核字段，**即待审队列之一**） |
| 共用 | `user_upload_photo` | 用户上传照片（**即待审队列之二**） |
| 共用 | `user_favorite` / `user_like` | 收藏、点赞（用户行为追溯） |
| 前台用户 | `user_permission_audit` | 用户状态/权限变更审计 |
| 管理员 | `admin_users` | 后台管理员（`role_id` 唯一角色） |
| RBAC | `role_definitions` / `permission_definitions` / `role_permission_assignments` | 角色权限 |
| RBAC | `admin_role_permission_audit` | 角色/权限变更审计 |
| 审核 | `sensitive_words` | 敏感词 |
| 审核 | `review_strategy_config` | 自动审核策略（单行 id=1） |
| 文物 | `artifact` | 文物数据（7 张共用表） |
| 备份 | `backup_records` / `backup_task_config` / `restore_logs` | 备份与恢复 |
| 日志 | `operation_logs` / `login_logs` / `system_logs` / `data_change_logs` | 四类日志 |

## 已废弃（勿再建表）

- `unified_users`、`platform_users` → `user`
- `unified_user_behaviors`、`user_behavior_records`、**`user_behaviors`** → 直接查 `comment` / `user_upload_photo` / `user_favorite` / `user_like`
- **`review_contents`** → 直接查 `comment` + `user_upload_photo`
- `unified_user_permission_audit` → `user_permission_audit`
- `admin_role_assignments` → `admin_users.role_id`

## 选做扩展表

`review_statistics_daily`、`dashboard_metrics_daily`、`security_logs`、`user_violation_records`、`user_violation_appeals`、`system_announcements`、`system_feature_flags`、`data_consistency_reports`、`system_alerts`、`user_posts`、`user_upload_video`、`user_upload_audio`

## 代码对照

| 实体类 | 表名 |
|--------|------|
| User | user |
| UserPermissionAudit | user_permission_audit |
| AdminUser | admin_users |
| AdminRolePermissionAudit | admin_role_permission_audit |
| RestoreLog | restore_logs |

详细字段见 `docs/schema-full.sql` 或各 `entity` 类 `@Column` 定义。
