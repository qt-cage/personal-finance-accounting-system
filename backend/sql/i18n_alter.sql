-- ============================================================
-- 全局多语言切换功能 - 数据库修改脚本
-- ============================================================

-- 1. user 表新增 language 字段（varchar(10)，默认zh-cn）
ALTER TABLE `user`
    ADD COLUMN `language` VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci
    DEFAULT 'zh-cn' COMMENT '用户语言偏好：zh-cn-中文 en-英文 ja-日语' AFTER `avatar`;
