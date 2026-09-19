package com.finance.service;

import com.finance.entity.dto.UpdateUserProfileDTO;
import com.finance.entity.dto.UserProfileDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface UserService {

    Map<String, Object> login(String username, String password);

    void register(String username, String password, String nickname);

    UserProfileDTO getUserInfo(Long userId);

    void updateUserInfo(Long userId, String nickname, String avatar, String language, String theme);

    void updateUserProfile(Long userId, UpdateUserProfileDTO dto);

    String uploadAvatar(Long userId, MultipartFile file);

    void clearAvatar(Long userId);

    void changePassword(Long userId, String oldPassword, String newPassword);

    void deleteAccount(Long userId);
}
