# 子系统5（后台管理）数据库表设计终稿

> 与代码实体对齐版本。共用库重建时以本文为准；API 路径 `/api/admin/unified-users` 保留，底层表为 `users`。

## 表清单（26 张核心表）

| 分类 | 表名 | 说明 |
|------|------|------|
| 前台用户 | `users` | 全平台前台用户主表（原 unified_users + platform_users） |
| 前台用户 | `user_behaviors` | 用户行为记录 |
| 前台用户 | `user_permission_audit` | 用户状态/权限变更审计 |
| 共用改字段 | `comment` | 评论（Web/App 维护，后台扩展审核字段） |
| 共用改字段 | `user_upload_photo` | 用户上传照片 |
| 管理员 | `admin_users` | 后台管理员（`role_id` 唯一角色） |
| RBAC | `role_definitions` | 角色 |
| RBAC | `permission_definitions` | 权限 |
| RBAC | `role_permission_assignments` | 角色-权限 |
| RBAC | `admin_role_permission_audit` | 角色/权限变更审计 |
| 审核 | `review_contents` | 统一待审队列 |
| 审核 | `sensitive_words` | 敏感词 |
| 审核 | `review_strategy_config` | 自动审核策略（单行 id=1） |
| 文物 | `artifacts` | 文物数据 |
| 备份 | `backup_records` | 备份记录 |
| 备份 | `backup_task_config` | 定时备份配置（单行 id=1） |
| 备份 | `restore_logs` | 恢复审计 |
| 日志 | `operation_logs` | 操作日志 |
| 日志 | `login_logs` | 登录日志 |
| 日志 | `system_logs` | 系统日志 |
| 日志 | `data_change_logs` | 数据变更日志 |

## 已废弃（勿再建表）

- `unified_users`、`platform_users` → `users`
- `unified_user_behaviors`、`user_behavior_records` → `user_behaviors`
- `unified_user_permission_audit` → `user_permission_audit`
- `admin_role_assignments` → `admin_users.role_id`

## 选做扩展表

`review_statistics_daily`、`dashboard_metrics_daily`、`security_logs`、`user_violation_records`、`user_violation_appeals`、`system_announcements`、`system_feature_flags`、`data_consistency_reports`、`system_alerts`、`user_posts`、`user_upload_video`、`user_upload_audio`

## 代码对照

| 实体类 | 表名 |
|--------|------|
| User | users |
| UserBehavior | user_behaviors |
| UserPermissionAudit | user_permission_audit |
| AdminUser | admin_users |
| AdminRolePermissionAudit | admin_role_permission_audit |
| RestoreLog | restore_logs |

详细字段见上一轮设计文档或各 `entity` 类 `@Column` 定义。
