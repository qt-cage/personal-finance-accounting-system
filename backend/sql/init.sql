CREATE DATABASE IF NOT EXISTS finance_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE finance_db;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `token_version` INT NOT NULL DEFAULT 0 COMMENT '登录令牌版本',
    `role` VARCHAR(10) NOT NULL DEFAULT 'USER',
    `status` VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
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
    `parent_id` BIGINT DEFAULT NULL COMMENT '父分类ID，NULL为一级分类',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `type` TINYINT NOT NULL COMMENT '类型 1-支出 2-收入',
    `icon` VARCHAR(50) DEFAULT NULL COMMENT '图标',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_category_parent` (`parent_id`),
    `active_key` TINYINT GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) STORED,
    UNIQUE KEY `uk_category_active` (`user_id`, `name`, `type`, `active_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- 账单表
CREATE TABLE IF NOT EXISTS `bill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `category_id` BIGINT DEFAULT NULL COMMENT '收支分类ID，转账无分类',
    `account_id` BIGINT DEFAULT NULL COMMENT '收支账户或转出账户，NULL为待归属',
    `transfer_account_id` BIGINT DEFAULT NULL COMMENT '转入账户，仅转账使用',
    `request_id` VARCHAR(64) DEFAULT NULL COMMENT '转账幂等请求编号',
    `amount` DECIMAL(12,2) NOT NULL COMMENT '金额',
    `type` TINYINT NOT NULL COMMENT '类型 1-支出 2-收入 3-转账',
    `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
    `bill_date` DATE NOT NULL COMMENT '账单日期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_date` (`user_id`, `bill_date`),
    KEY `idx_user_type` (`user_id`, `type`),
    KEY `idx_bill_account_date` (`user_id`, `account_id`, `bill_date`),
    KEY `idx_bill_transfer_date` (`user_id`, `transfer_account_id`, `bill_date`),
    UNIQUE KEY `uk_bill_request` (`user_id`, `request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账单表';

-- 财务账户表；期初余额为带符号金额，负值表示欠款。
CREATE TABLE IF NOT EXISTS `finance_account` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `account_type` VARCHAR(20) NOT NULL,
    `opening_balance` DECIMAL(12,2) NOT NULL DEFAULT 0,
    `opening_date` DATE NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    `active_key` TINYINT GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_active` (`user_id`, `name`, `active_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人财务账户';

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

CREATE TABLE IF NOT EXISTS recurring_bill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(50) NOT NULL,
    type TINYINT NOT NULL,
    category_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    remark VARCHAR(200) DEFAULT NULL,
    frequency VARCHAR(10) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE DEFAULT NULL,
    next_date DATE NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    last_error VARCHAR(200) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    KEY idx_recurring_due(deleted,enabled,next_date),
    KEY idx_recurring_user(user_id,deleted),
    KEY idx_recurring_account(account_id,deleted),
    KEY idx_recurring_category(category_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recurring_run (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    bill_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(id),
    UNIQUE KEY uk_recurring_occurrence(template_id,due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bill_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    bill_id BIGINT NOT NULL,
    filename VARCHAR(100) NOT NULL,
    storage_name VARCHAR(40) NOT NULL,
    media_type VARCHAR(20) NOT NULL,
    size BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY(id),
    KEY idx_attachment_owner(user_id,bill_id,deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notification_preference (
 user_id BIGINT NOT NULL PRIMARY KEY,
 enabled TINYINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS notification (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 budget_id BIGINT NOT NULL,
 month CHAR(7) NOT NULL,
 stage INT NOT NULL,
 content VARCHAR(300) NOT NULL,
 read_flag TINYINT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_budget_notice (user_id,budget_id,month,stage),
 KEY idx_notice_owner (user_id,read_flag,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_dictionary (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 kind VARCHAR(20) NOT NULL,
 code VARCHAR(20) NOT NULL,
 name VARCHAR(50) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 sort_order INT NOT NULL DEFAULT 0,
 UNIQUE KEY uk_dictionary_code(kind,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS system_setting (
 name VARCHAR(30) NOT NULL PRIMARY KEY,
 value VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS operation_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 actor_id BIGINT NOT NULL,
 method VARCHAR(10) NOT NULL,
 route VARCHAR(160) NOT NULL,
 outcome INT NOT NULL,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY idx_operation_actor(actor_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS data_revision (
 user_id BIGINT NOT NULL PRIMARY KEY,
 revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO data_revision(user_id,revision) VALUES(0,0);

INSERT IGNORE INTO system_dictionary(kind,code,name,enabled,sort_order) VALUES
 ('CURRENCY','CNY','人民币',1,1),
 ('ACCOUNT_TYPE','CASH','现金',1,1),('ACCOUNT_TYPE','BANK','银行卡',1,2),
 ('ACCOUNT_TYPE','ALIPAY','支付宝',1,3),('ACCOUNT_TYPE','WECHAT','微信',1,4),
 ('ACCOUNT_TYPE','CREDIT','信用卡',1,5),('ACCOUNT_TYPE','INVESTMENT','投资账户',1,6);
INSERT IGNORE INTO system_setting(name,value) VALUES('siteName','个人财务记账系统'),('registrationEnabled','true');

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
