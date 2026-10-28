package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("recurring_bill")
public class RecurringBill {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String name;
    private Integer type;
    private Long categoryId;
    private Long accountId;
    private BigDecimal amount;
    private String remark;
    private String frequency;
    private LocalDate startDate;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private LocalDate endDate;
    private LocalDate nextDate;
    private Boolean enabled;
    @TableField(updateStrategy = FieldStrategy.IGNORED) private String lastError;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
    @TableField(exist = false) private Boolean hasGenerated;
}
