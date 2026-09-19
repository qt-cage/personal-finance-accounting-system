package com.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    /** 改密时递增，用于使已签发的登录令牌失效；部分更新时必须保留 null。 */
    private Integer tokenVersion;

    private String nickname;

    private String avatar;

    /** 用户语言偏好：zh-cn / en / ja */
    private String language;

    /** 主题偏好：light / dark / eye-care */
    private String theme;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
