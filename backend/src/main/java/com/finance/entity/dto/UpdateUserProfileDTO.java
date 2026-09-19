package com.finance.entity.dto;

import lombok.Data;

/**
 * 个人资料局部更新请求。用户名和头像不通过该接口修改。
 */
@Data
public class UpdateUserProfileDTO {

    private String nickname;

    private String language;

    private String theme;
}
