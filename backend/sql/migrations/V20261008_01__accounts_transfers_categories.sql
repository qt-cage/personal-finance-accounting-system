-- 在已选择的数据库执行；不创建/切换数据库，不分配旧账单账户，不猜测期初余额。
-- MySQL DDL会隐式提交；真实账本部署前须审核备份与恢复步骤，不能仅依赖ROLLBACK。
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

DELIMITER $$
DROP PROCEDURE IF EXISTS finance_b1_schema_upgrade_20261008$$
CREATE PROCEDURE finance_b1_schema_upgrade_20261008()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='category' AND column_name='parent_id') THEN
        ALTER TABLE category ADD COLUMN parent_id BIGINT DEFAULT NULL COMMENT '父分类ID，NULL为一级分类' AFTER user_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='category' AND index_name='idx_category_parent') THEN
        ALTER TABLE category ADD KEY idx_category_parent(parent_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bill' AND column_name='account_id') THEN
        ALTER TABLE bill ADD COLUMN account_id BIGINT DEFAULT NULL COMMENT '收支账户或转出账户，NULL为待归属' AFTER category_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bill' AND column_name='transfer_account_id') THEN
        ALTER TABLE bill ADD COLUMN transfer_account_id BIGINT DEFAULT NULL COMMENT '转入账户，仅转账使用' AFTER account_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bill' AND column_name='request_id') THEN
        ALTER TABLE bill ADD COLUMN request_id VARCHAR(64) DEFAULT NULL COMMENT '转账幂等请求编号' AFTER transfer_account_id;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='bill' AND column_name='category_id' AND is_nullable='NO') THEN
        ALTER TABLE bill MODIFY COLUMN category_id BIGINT DEFAULT NULL COMMENT '收支分类ID，转账无分类';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='bill' AND index_name='idx_bill_account_date') THEN
        ALTER TABLE bill ADD KEY idx_bill_account_date(user_id,account_id,bill_date);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='bill' AND index_name='idx_bill_transfer_date') THEN
        ALTER TABLE bill ADD KEY idx_bill_transfer_date(user_id,transfer_account_id,bill_date);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='bill' AND index_name='uk_bill_request') THEN
        ALTER TABLE bill ADD UNIQUE KEY uk_bill_request(user_id,request_id);
    END IF;
END$$
CALL finance_b1_schema_upgrade_20261008()$$
DROP PROCEDURE finance_b1_schema_upgrade_20261008$$
DELIMITER ;
