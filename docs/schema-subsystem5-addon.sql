-- =============================================================================
-- 子系统5（后台管理）— 追加建表脚本
-- =============================================================================
-- 用途：
--   在「最终共用库」已有 表结构.sql 的 7 张表基础上，只追加本子系统需要的表。
--   不要在最终库上执行 DROP DATABASE / 不要 DROP 已有业务表。
--
-- 最终库现状（其他子系统已建好，来自 d:\表结构.sql）：
--   admin_user, artifact, user, comment, user_favorite, user_like, user_upload_photo
--
-- 本脚本追加：
--   1) 扩展 user 表 2 列（can_comment, can_upload）
--   2) 新建 17 张子系统5 专表
--
-- 本地测试（库名随意，例如 overseas_artifacts）：
--   ① 先导入 d:\表结构.sql
--   ② 再执行本脚本
--
-- 最终上线（连真实共用库时）：
--   只执行本脚本一次即可（在已有 7 张表的基础上）
--
-- 执行示例：
--   mysql -u root -p 你的库名 < docs/schema-subsystem5-addon.sql
-- =============================================================================

/*!40101 SET NAMES utf8mb4 */;
/*!40014 SET FOREIGN_KEY_CHECKS=0 */;

-- -----------------------------------------------------------------------------
-- 1. 扩展共用 user 表（若列已存在会报错，可忽略或注释掉对应语句）
-- -----------------------------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN `can_comment` tinyint(1) NOT NULL DEFAULT '1' COMMENT '【子系统5】是否允许评论' AFTER `disabled_at`,
  ADD COLUMN `can_upload`  tinyint(1) NOT NULL DEFAULT '1' COMMENT '【子系统5】是否允许上传' AFTER `can_comment`;

-- -----------------------------------------------------------------------------
-- 2. RBAC 与管理员（子系统5 新建，与旧 admin_user 并存）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色编码，如 SUPER_ADMIN',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色名称',
  `description` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_system` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否系统内置角色',
  `status` int NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色定义';

CREATE TABLE IF NOT EXISTS `permission_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限编码',
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限名称',
  `module` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模块',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作',
  `description` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限定义';

CREATE TABLE IF NOT EXISTS `role_permission_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_rpa_role` (`role_id`),
  KEY `idx_rpa_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联';

CREATE TABLE IF NOT EXISTS `admin_users` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '管理员ID（子系统5主表）',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `role_id` bigint NOT NULL COMMENT '唯一角色 FK→role_definitions.id',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `display_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_users_username` (`username`),
  KEY `idx_admin_users_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='后台管理员（子系统5 RBAC）';

CREATE TABLE IF NOT EXISTS `admin_role_permission_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ROLE/PERMISSION/ADMIN_USER',
  `target_id` bigint NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `before_snapshot` json DEFAULT NULL,
  `after_snapshot` json DEFAULT NULL,
  `reason` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_arpa_operated_at` (`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色/权限变更审计';

-- -----------------------------------------------------------------------------
-- 3. 用户权限审计
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_permission_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'FK→user.user_id',
  `operator_id` bigint NOT NULL,
  `operator_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `old_status` int DEFAULT NULL,
  `new_status` int DEFAULT NULL,
  `old_can_comment` tinyint(1) DEFAULT NULL,
  `new_can_comment` tinyint(1) DEFAULT NULL,
  `old_can_upload` tinyint(1) DEFAULT NULL,
  `new_can_upload` tinyint(1) DEFAULT NULL,
  `reason` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_upa_user_time` (`user_id`,`operated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户状态/权限变更审计';

-- -----------------------------------------------------------------------------
-- 4. 内容审核（待审队列直接使用 comment + user_upload_photo，不建 review_contents）
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sensitive_words` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT '1',
  `level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LIGHT',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词库';

CREATE TABLE IF NOT EXISTS `review_strategy_config` (
  `id` bigint NOT NULL COMMENT '固定为 1',
  `low_risk_max_score` int NOT NULL DEFAULT '20',
  `medium_risk_max_score` int NOT NULL DEFAULT '60',
  `low_risk_action` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO_APPROVE',
  `medium_risk_action` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL_REVIEW',
  `high_risk_action` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'AUTO_REJECT',
  `updated_by` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动审核策略（单行配置）';

-- -----------------------------------------------------------------------------
-- 5. 备份与恢复
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `backup_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `file_name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_path` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `backup_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'FULL/TABLE/...',
  `table_scope` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint NOT NULL DEFAULT '0',
  `encrypted` tinyint(1) NOT NULL DEFAULT '1',
  `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUCCESS',
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `operator` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `backup_time` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_br_backup_time` (`backup_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份记录';

CREATE TABLE IF NOT EXISTS `backup_task_config` (
  `id` bigint NOT NULL COMMENT '固定为 1',
  `auto_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `cron_expression` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0 0 2 * * *',
  `retention_days` int NOT NULL DEFAULT '30',
  `last_auto_run` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时备份配置（单行）';

CREATE TABLE IF NOT EXISTS `restore_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `backup_record_id` bigint NOT NULL,
  `operator_id` bigint NOT NULL,
  `confirm_text` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  `restore_scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `table_scope` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) NOT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rl_backup` (`backup_record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='恢复操作审计';

-- -----------------------------------------------------------------------------
-- 6. 日志
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `module` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_target` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `before_data` json DEFAULT NULL,
  `after_data` json DEFAULT NULL,
  `details` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ip_address` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_op_time` (`operation_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志';

CREATE TABLE IF NOT EXISTS `login_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN',
  `user_id` bigint DEFAULT NULL,
  `username` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

CREATE TABLE IF NOT EXISTS `system_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `level` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stack_trace` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `log_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sys_log_time` (`log_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志';

CREATE TABLE IF NOT EXISTS `data_change_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `change_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `before_data` json DEFAULT NULL,
  `after_data` json DEFAULT NULL,
  `detail` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `change_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dcl_change_time` (`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据变更日志';

/*!40014 SET FOREIGN_KEY_CHECKS=1 */;

-- 追加完成。本子系统向最终库新增内容：
--   ALTER user 表 + 17 张新表（不修改、不删除其他子系统已有表）
--
-- 若 login_logs 已存在但无 source_system，请另执行 docs/migration-login-source-system.sql
