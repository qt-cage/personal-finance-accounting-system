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
