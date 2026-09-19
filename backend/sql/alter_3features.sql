-- 主题偏好字段
ALTER TABLE `user`
    ADD COLUMN `theme` VARCHAR(20) DEFAULT 'light'
    COMMENT '主题偏好：light-亮色 dark-暗色 eye-care-护眼' AFTER `language`;
