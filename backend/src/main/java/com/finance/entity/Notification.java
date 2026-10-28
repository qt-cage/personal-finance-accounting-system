package com.finance.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@TableName("notification")
public class Notification {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private Long budgetId;
    private String month;
    private Integer stage;
    private String content;
    private Boolean readFlag;
    private LocalDateTime createTime;
}
