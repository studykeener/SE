-- login_logs.source_system 改为非空（web/app/admin）
-- 在已有库上执行一次即可

UPDATE `login_logs`
SET `source_system` = 'admin'
WHERE `source_system` IS NULL OR TRIM(`source_system`) = '';

ALTER TABLE `login_logs`
  MODIFY COLUMN `source_system` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL
  COMMENT 'web/app/admin';
