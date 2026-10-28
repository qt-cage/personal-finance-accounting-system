package com.finance.controller;

import com.finance.common.result.Result;
import com.finance.entity.dto.ChangePasswordDTO;
import com.finance.entity.dto.AvatarUploadResponse;
import com.finance.entity.dto.LoginDTO;
import com.finance.entity.dto.RegisterDTO;
import com.finance.entity.dto.UpdateUserProfileDTO;
import com.finance.entity.dto.UserProfileDTO;
import com.finance.service.UserService;
import com.finance.common.i18n.I18nUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private I18nUtil i18nUtil;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Validated @RequestBody LoginDTO dto) {
        Map<String, Object> result = userService.login(dto.getUsername(), dto.getPassword());
        return Result.success(result, i18nUtil.getMessage("user.login.success"));
    }

    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterDTO dto) {
        userService.register(dto.getUsername(), dto.getPassword(), dto.getNickname());
        return Result.success(i18nUtil.getMessage("user.register.success"));
    }

    @GetMapping("/info")
    public Result<UserProfileDTO> info(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return Result.success(userService.getUserInfo(userId));
    }

    @PutMapping("/info")
    public Result<Void> updateInfo(HttpServletRequest request,
                                   @RequestParam(required = false) String nickname,
                                   @RequestParam(required = false) String avatar,
                                   @RequestParam(required = false) String language,
                                   @RequestParam(required = false) String theme) {
        Long userId = (Long) request.getAttribute("userId");
        userService.updateUserInfo(userId, nickname, avatar, language, theme);
        return Result.success(i18nUtil.getMessage("user.info.update"));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(HttpServletRequest request,
                                      @RequestBody UpdateUserProfileDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        userService.updateUserProfile(userId, dto);
        return Result.success(i18nUtil.getMessage("user.info.update"));
    }

    @PostMapping("/avatar")
    public Result<AvatarUploadResponse> uploadAvatar(HttpServletRequest request,
                                                     @RequestParam("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute("userId");
        String avatarUrl = userService.uploadAvatar(userId, file);
        return Result.success(new AvatarUploadResponse(avatarUrl),
                i18nUtil.getMessage("user.avatar.upload.success"));
    }

    @DeleteMapping("/avatar")
    public Result<Void> clearAvatar(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.clearAvatar(userId);
        return Result.success(i18nUtil.getMessage("user.avatar.delete.success"));
    }

    @PutMapping("/password")
    public Result<Void> changePassword(HttpServletRequest request, @Validated @RequestBody ChangePasswordDTO dto) {
        Long userId = (Long) request.getAttribute("userId");
        userService.changePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success(i18nUtil.getMessage("user.password.change.success"));
    }

    @DeleteMapping("/account")
    public Result<Void> deleteAccount(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        userService.deleteAccount(userId);
        return Result.success(i18nUtil.getMessage("user.account.delete.success"));
    }
}
