package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("category")
public class Category {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    // 本项目3.5.3.1用IGNORED显式写入null；服务层区分未传字段与主动清除父分类。
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long parentId;

    private String name;

    private Integer type;

    private String icon;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
