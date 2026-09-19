package com.finance.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 头像上传结果。
 */
@Data
@AllArgsConstructor
public class AvatarUploadResponse {

    private String avatarUrl;
}
