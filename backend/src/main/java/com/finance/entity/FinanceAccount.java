package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("finance_account")
public class FinanceAccount {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String accountType;
    /** Signed opening balance: outstanding debt is negative. */
    private BigDecimal openingBalance;
    private LocalDate openingDate;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
    @TableField(exist = false)
    private BigDecimal balance;
    @TableField(exist = false)
    private Boolean hasTransactions;
    @TableField(exist = false)
    private Boolean hasPlans;
    @TableField(exist = false)
    private LocalDate asOfDate;
}
