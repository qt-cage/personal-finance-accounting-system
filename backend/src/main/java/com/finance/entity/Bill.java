package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("bill")
public class Bill {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long categoryId;

    private Long accountId;

    private Long transferAccountId;

    /** 转账幂等键不因逻辑删除而释放，防止重试恢复已撤销转账。 */
    private String requestId;

    private BigDecimal amount;

    private Integer type;

    private String remark;

    private LocalDate billDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    @TableField(exist = false)
    private String categoryName;

    @TableField(exist = false)
    private String categoryIcon;

    @TableField(exist = false)
    private String accountName;

    @TableField(exist = false)
    private String transferAccountName;
}
