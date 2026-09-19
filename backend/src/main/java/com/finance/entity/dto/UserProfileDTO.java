package com.finance.entity.dto;

import com.finance.entity.User;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面向前端的个人资料响应，只暴露用户可见字段。
 */
@Data
public class UserProfileDTO {

    private String username;

    private String nickname;

    private String avatar;

    private String language;

    private String theme;

    private LocalDateTime createTime;

    public static UserProfileDTO from(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setAvatar(user.getAvatar());
        dto.setLanguage(user.getLanguage() != null ? user.getLanguage() : "zh-cn");
        dto.setTheme(user.getTheme() != null ? user.getTheme() : "light");
        dto.setCreateTime(user.getCreateTime());
        return dto;
    }
}
