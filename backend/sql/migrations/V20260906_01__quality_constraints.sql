-- 在目标数据库连接中执行；执行前备份，停止应用写入。
-- 重复执行安全；发现有效重复数据时主动停止，不合并或删除用户记录。
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(64) NOT NULL PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DROP PROCEDURE IF EXISTS migrate_quality_20260906;
DELIMITER $$
CREATE PROCEDURE migrate_quality_20260906()
BEGIN
    IF EXISTS (SELECT 1 FROM budget WHERE deleted = 0
        GROUP BY user_id, month, COALESCE(category_id, 0) HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate active budgets: review data before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM category WHERE deleted = 0
        GROUP BY user_id, name, type HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate active categories: review data before migration';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='user' AND column_name='token_version') THEN
        ALTER TABLE `user` ADD COLUMN token_version INT NOT NULL DEFAULT 0 AFTER password;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='category' AND column_name='active_key') THEN
        ALTER TABLE category ADD COLUMN active_key TINYINT GENERATED ALWAYS AS
            (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='category' AND index_name='uk_category_active') THEN
        ALTER TABLE category ADD UNIQUE KEY uk_category_active(user_id,name,type,active_key);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='category' AND index_name='uk_user_name_type') THEN
        ALTER TABLE category DROP INDEX uk_user_name_type;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='budget' AND column_name='category_key') THEN
        ALTER TABLE budget ADD COLUMN category_key BIGINT GENERATED ALWAYS AS (COALESCE(category_id,0)) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='budget' AND column_name='active_key') THEN
        ALTER TABLE budget ADD COLUMN active_key TINYINT GENERATED ALWAYS AS
            (CASE WHEN deleted = 0 THEN 1 ELSE NULL END) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
        AND table_name='budget' AND index_name='uk_budget_active') THEN
        ALTER TABLE budget ADD UNIQUE KEY uk_budget_active(user_id,month,category_key,active_key);
    END IF;
    INSERT IGNORE INTO schema_migrations(version) VALUES ('20260906_01');
END$$
DELIMITER ;
CALL migrate_quality_20260906();
DROP PROCEDURE migrate_quality_20260906;
