-- 删除子系统5 中未使用的冗余表（已有库执行一次）
-- 不影响：内容审核（comment/user_upload_photo）、用户行为追溯（7 张共用表）

DROP TABLE IF EXISTS `review_contents`;
DROP TABLE IF EXISTS `user_behaviors`;
