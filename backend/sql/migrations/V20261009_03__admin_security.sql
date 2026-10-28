-- 在已选择的数据库执行；不自动提升任何真实用户的管理员权限。
DELIMITER $$
DROP PROCEDURE IF EXISTS finance_b4_schema_upgrade_20261009$$
CREATE PROCEDURE finance_b4_schema_upgrade_20261009()
BEGIN
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user' AND column_name='role') THEN
  ALTER TABLE `user` ADD COLUMN `role` VARCHAR(10) NOT NULL DEFAULT 'USER' AFTER token_version;
 END IF;
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user' AND column_name='status') THEN
  ALTER TABLE `user` ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' AFTER `role`;
 END IF;
END$$
CALL finance_b4_schema_upgrade_20261009()$$
DROP PROCEDURE finance_b4_schema_upgrade_20261009$$
DELIMITER ;
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
INSERT IGNORE INTO system_dictionary(kind,code,name,enabled,sort_order) VALUES
 ('CURRENCY','CNY','人民币',1,1),
 ('ACCOUNT_TYPE','CASH','现金',1,1),('ACCOUNT_TYPE','BANK','银行卡',1,2),
 ('ACCOUNT_TYPE','ALIPAY','支付宝',1,3),('ACCOUNT_TYPE','WECHAT','微信',1,4),
 ('ACCOUNT_TYPE','CREDIT','信用卡',1,5),('ACCOUNT_TYPE','INVESTMENT','投资账户',1,6);
INSERT IGNORE INTO system_setting(name,value) VALUES('siteName','个人财务记账系统'),('registrationEnabled','true');
