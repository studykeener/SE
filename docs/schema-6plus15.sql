-- =============================================================================
-- 完整建库脚本：6 张共用表 + 15 张子系统5 表 = 21 张（不含已废弃 admin_user）
-- =============================================================================
-- 执行示例（本地重建）：
--   mysql -u root -p123456 < D:\SE\admin-backend\docs\schema-6plus15.sql
-- =============================================================================

/*!40101 SET NAMES utf8mb4 */;
/*!40014 SET FOREIGN_KEY_CHECKS=0 */;
/*!40014 SET UNIQUE_CHECKS=0 */;

DROP DATABASE IF EXISTS `overseas_artifacts`;
CREATE DATABASE `overseas_artifacts`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `overseas_artifacts`;

-- =============================================================================
-- 第一部分：6 张全组共用业务表
-- =============================================================================

-- ----------------------------
-- Table: artifact（三馆文物主数据）
-- ----------------------------
DROP TABLE IF EXISTS `artifact`;
CREATE TABLE `artifact` (
  `object_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物唯一编号（馆方/EDAN）',
  `artifact_id` varchar(256) COLLATE utf8mb4_unicode_ci NOT NULL,
  `museum_id` int NOT NULL COMMENT '馆别：1史密森尼 2哈佛 3波士顿MFA',
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物名称',
  `artist` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作者/制作者',
  `artist_province` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作者相关省份（推断）',
  `dynasty` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '朝代',
  `artist_wikidata_id` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT 'Wikidata Q号',
  `artist_birth` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '作者生年',
  `artist_death` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '作者卒年',
  `artist_bio` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '作者简介',
  `artist_wikipedia_summary` varchar(4000) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '维基摘要',
  `artist_enriched_at` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '作者信息补全时间',
  `period` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '年代/时期原文',
  `period_start_year` smallint DEFAULT NULL COMMENT '起始年',
  `period_end_year` smallint DEFAULT NULL COMMENT '结束年',
  `type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物类型',
  `material` text COLLATE utf8mb4_unicode_ci COMMENT '材质',
  `culture` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文化/地域标签',
  `description` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物介绍',
  `provenance` text COLLATE utf8mb4_unicode_ci COMMENT '流传经历',
  `bibliography` text COLLATE utf8mb4_unicode_ci COMMENT '参考文献',
  `dimensions` text COLLATE utf8mb4_unicode_ci,
  `museum` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属博物馆',
  `location` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '博物馆所在地',
  `detail_url` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '详情页URL',
  `image_url` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图片原始URL',
  `image_urls` text COLLATE utf8mb4_unicode_ci,
  `iiif_manifest_url` text COLLATE utf8mb4_unicode_ci COMMENT 'IIIF manifest（哈佛）',
  `image_path` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '本地相对图片路径',
  `image_paths` text COLLATE utf8mb4_unicode_ci,
  `image_count` smallint NOT NULL DEFAULT '0',
  `credit_line` text COLLATE utf8mb4_unicode_ci,
  `accession_number` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '藏品编号',
  `crawl_date` date NOT NULL COMMENT '爬取日期',
  PRIMARY KEY (`museum_id`,`object_id`),
  KEY `idx_artifact_id` (`artifact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='海外藏中国文物—三馆统一藏品表';

-- ----------------------------
-- Table: user（Web/App 前台用户 + 子系统5 权限扩展列）
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `user_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `user_source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'web' COMMENT '注册来源：web / app',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '展示昵称',
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `sex` tinyint DEFAULT NULL COMMENT '性别：0未知 1男 2女',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（加密存储，应用层 bcrypt 等）',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `register_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `last_login_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最近登录IP',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1正常 0禁用',
  `disabled_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '禁用原因',
  `disabled_by` bigint unsigned DEFAULT NULL COMMENT '禁用操作管理员ID',
  `disabled_at` datetime DEFAULT NULL COMMENT '禁用时间',
  `can_comment` tinyint(1) NOT NULL DEFAULT '1' COMMENT '【子系统5】是否允许评论',
  `can_upload` tinyint(1) NOT NULL DEFAULT '1' COMMENT '【子系统5】是否允许上传',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '信息更新时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_status_register` (`status`,`register_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='前台用户（Web/App 共用）';

-- ----------------------------
-- Table: comment
-- ----------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `comment_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  `user_id` bigint unsigned NOT NULL COMMENT '评论用户ID',
  `museum_id` int NOT NULL COMMENT '文物馆别，对齐 artifact.museum_id',
  `object_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物编号，对齐 artifact.object_id',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源：web / app',
  `audit_method` tinyint NOT NULL DEFAULT '1' COMMENT '1自动 2人工 3自动+人工',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审 1通过 2拒绝 3复审',
  `auto_audit_status` tinyint DEFAULT NULL COMMENT '自动审核结果',
  `sensitive_words_hit` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中敏感词，逗号分隔',
  `auditor_id` bigint unsigned DEFAULT NULL COMMENT '审核员 admin_users.id',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1显示 0用户删 2后台屏蔽',
  `deleted_by` bigint unsigned DEFAULT NULL COMMENT '删除/屏蔽操作管理员ID',
  `delete_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '删除或屏蔽原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`comment_id`),
  KEY `idx_user` (`user_id`,`created_at`),
  KEY `idx_artifact` (`museum_id`,`object_id`,`audit_status`,`created_at`),
  KEY `idx_audit` (`audit_status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文物评论（须审核后展示）';

-- ----------------------------
-- Table: user_favorite
-- ----------------------------
DROP TABLE IF EXISTS `user_favorite`;
CREATE TABLE `user_favorite` (
  `favorite_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键（原稿 save_id）',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `museum_id` int NOT NULL COMMENT '文物馆别',
  `object_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物编号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`favorite_id`),
  UNIQUE KEY `uk_user_artifact` (`user_id`,`museum_id`,`object_id`),
  KEY `idx_artifact` (`museum_id`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏文物';

-- ----------------------------
-- Table: user_like
-- ----------------------------
DROP TABLE IF EXISTS `user_like`;
CREATE TABLE `user_like` (
  `like_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
  `museum_id` int NOT NULL COMMENT '文物馆别',
  `object_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文物编号',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_user_artifact` (`user_id`,`museum_id`,`object_id`),
  KEY `idx_artifact` (`museum_id`,`object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户对文物的点赞';

-- ----------------------------
-- Table: user_upload_photo
-- ----------------------------
DROP TABLE IF EXISTS `user_upload_photo`;
CREATE TABLE `user_upload_photo` (
  `photo_id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '照片ID',
  `user_id` bigint unsigned NOT NULL COMMENT '上传用户ID',
  `museum_id` int DEFAULT NULL COMMENT '关联文物馆别（可选）',
  `object_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联文物编号（可选）',
  `photo_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '照片存储路径或URL',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文字描述',
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拍摄地点',
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源：web / app',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审 1通过 2拒绝 3复审 4屏蔽',
  `audit_method` tinyint NOT NULL DEFAULT '1' COMMENT '1自动 2人工 3自动+人工',
  `auto_audit_status` tinyint DEFAULT NULL COMMENT '图片自动审核结果',
  `auto_audit_score` decimal(5,2) DEFAULT NULL COMMENT '违规风险分 0-100',
  `auditor_id` bigint unsigned DEFAULT NULL COMMENT '审核员 admin_users.id',
  `reject_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`photo_id`),
  KEY `idx_user` (`user_id`,`created_at`),
  KEY `idx_artifact` (`museum_id`,`object_id`),
  KEY `idx_audit` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户上传文物照片（须审核）';

-- =============================================================================
-- 第二部分：子系统5 — RBAC 与管理员（4 张）
-- =============================================================================

DROP TABLE IF EXISTS `role_permission_assignments`;
DROP TABLE IF EXISTS `admin_users`;
DROP TABLE IF EXISTS `permission_definitions`;
DROP TABLE IF EXISTS `role_definitions`;

CREATE TABLE `role_definitions` (
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

CREATE TABLE `permission_definitions` (
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

CREATE TABLE `role_permission_assignments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_rpa_role` (`role_id`),
  KEY `idx_rpa_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联';

CREATE TABLE `admin_users` (
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

CREATE TABLE `admin_role_permission_audit` (
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

-- =============================================================================
-- 第三部分：子系统5 — 用户权限审计（1 张）
-- =============================================================================

DROP TABLE IF EXISTS `user_permission_audit`;

CREATE TABLE `user_permission_audit` (
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

-- =============================================================================
-- 第四部分：子系统5 — 内容审核（2 张）
-- =============================================================================

DROP TABLE IF EXISTS `sensitive_words`;
DROP TABLE IF EXISTS `review_strategy_config`;

CREATE TABLE `sensitive_words` (
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

CREATE TABLE `review_strategy_config` (
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

-- =============================================================================
-- 第五部分：子系统5 — 备份与恢复（3 张）
-- =============================================================================

DROP TABLE IF EXISTS `restore_logs`;
DROP TABLE IF EXISTS `backup_records`;
DROP TABLE IF EXISTS `backup_task_config`;

CREATE TABLE `backup_records` (
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

CREATE TABLE `backup_task_config` (
  `id` bigint NOT NULL COMMENT '固定为 1',
  `auto_enabled` tinyint(1) NOT NULL DEFAULT '1',
  `cron_expression` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '0 0 2 * * *',
  `retention_days` int NOT NULL DEFAULT '30',
  `last_auto_run` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='定时备份配置（单行）';

CREATE TABLE `restore_logs` (
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

-- =============================================================================
-- 第六部分：子系统5 — 日志（4 张）
-- =============================================================================

DROP TABLE IF EXISTS `data_change_logs`;
DROP TABLE IF EXISTS `operation_logs`;
DROP TABLE IF EXISTS `login_logs`;
DROP TABLE IF EXISTS `system_logs`;

CREATE TABLE `operation_logs` (
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

CREATE TABLE `login_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ADMIN',
  `user_id` bigint DEFAULT NULL,
  `username` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_system` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'web/app/admin',
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_time` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';

CREATE TABLE `system_logs` (
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

CREATE TABLE `data_change_logs` (
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
/*!40014 SET UNIQUE_CHECKS=1 */;

-- 建表完成。共 21 张表：
--   6 张共用：artifact, user, comment, user_favorite, user_like, user_upload_photo
--   15 张子系统5：role_definitions, permission_definitions, role_permission_assignments,
--     admin_users, admin_role_permission_audit, user_permission_audit,
--     sensitive_words, review_strategy_config,
--     backup_records, backup_task_config, restore_logs,
--     operation_logs, login_logs, system_logs, data_change_logs
--   知识图谱数据在 Neo4j，不在 MySQL
