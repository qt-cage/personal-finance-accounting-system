package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("bill_attachment")
public class BillAttachment {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private Long billId;
    private String filename;
    @JsonIgnore private String storageName;
    private String mediaType;
    private Long size;
    @TableField(fill=FieldFill.INSERT) private LocalDateTime createTime;
    @TableLogic private Integer deleted;
}
