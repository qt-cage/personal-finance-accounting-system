CREATE DATABASE IF NOT EXISTS finance_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE finance_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `token_version` INT NOT NULL DEFAULT 0 COMMENT '登录令牌版本',
    `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `language` VARCHAR(10) DEFAULT 'zh-cn' COMMENT '用户语言偏好：zh-cn-中文 en-英文 ja-日语',
    `theme` VARCHAR(20) DEFAULT 'light' COMMENT '主题偏好：light-亮色 dark-暗色 eye-care-护眼',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 分类表
CREATE TABLE IF NOT EXISTS `category` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `type` TINYINT NOT NULL COMMENT '类型 1-支出 2-收入',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    `active_key` TINYINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY `uk_category_active` (`user_id`, `name`, `type`, `active_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- 账单表
CREATE TABLE IF NOT EXISTS `bill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `category_id` BIGINT NOT NULL COMMENT '分类ID',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '金额',
    `type` TINYINT NOT NULL COMMENT '类型 1-支出 2-收入',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `bill_date` DATE NOT NULL COMMENT '账单日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `bill_date`),
    KEY `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

-- 预算表
CREATE TABLE IF NOT EXISTS `budget` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `category_id` BIGINT DEFAULT NULL COMMENT '分类ID，NULL表示总预算',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '预算金额',
    `month` VARCHAR(7) NOT NULL COMMENT '月份 yyyy-MM',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_month` (`user_id`, `month`),
    `category_key` BIGINT GENERATED ALWAYS AS (COALESCE(category_id, 0)) STORED,
    `active_key` TINYINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY `uk_budget_active` (`user_id`, `month`, `category_key`, `active_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预算表';

-- 初始化默认分类（支出）
INSERT IGNORE INTO `category` (`user_id`, `name`, `type`, `icon`, `sort_order`) VALUES
(0, '餐饮', 1, 'food', 1),
(0, '交通', 1, 'transport', 2),
(0, '购物', 1, 'shopping', 3),
(0, '娱乐', 1, 'entertainment', 4),
(0, '居住', 1, 'housing', 5),
(0, '医疗', 1, 'medical', 6),
(0, '教育', 1, 'education', 7),
(0, '通讯', 1, 'phone', 8),
(0, '其他支出', 1, 'other', 99);

-- 初始化默认分类（收入）
INSERT IGNORE INTO `category` (`user_id`, `name`, `type`, `icon`, `sort_order`) VALUES
(0, '工资', 2, 'salary', 1),
(0, '奖金', 2, 'bonus', 2),
(0, '理财', 2, 'invest', 3),
(0, '兼职', 2, 'parttime', 4),
(0, '其他收入', 2, 'other', 99);
