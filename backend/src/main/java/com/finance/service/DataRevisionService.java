package com.finance.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class DataRevisionService {
    private final JdbcTemplate jdbc;
    public DataRevisionService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    /** 在业务事务中先锁定版本行，使失效与记账提交一致，并统一同用户写入的锁序。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void bump(Long owner){jdbc.update("INSERT INTO data_revision(user_id,revision) VALUES(?,1) ON DUPLICATE KEY UPDATE revision=revision+1",owner);}

    /** 复用同用户写锁而不改变聚合版本，用于批量预锁和附件配额预留。 */
    @Transactional(propagation=Propagation.MANDATORY)
    public void lock(Long owner){jdbc.update("INSERT INTO data_revision(user_id,revision) VALUES(?,0) ON DUPLICATE KEY UPDATE revision=revision",owner);}
}
