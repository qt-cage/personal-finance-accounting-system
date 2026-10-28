-- 在已选择且已完成B1迁移的数据库中执行，不创建或切换数据库。
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
