package com.finance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.common.util.JwtUtil;
import com.finance.entity.User;
import com.finance.entity.dto.UpdateUserProfileDTO;
import com.finance.entity.dto.UserProfileDTO;
import com.finance.mapper.UserMapper;
import com.finance.service.AvatarStorageService;
import com.finance.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private static final Set<String> SUPPORTED_LANGUAGES =
            new HashSet<>(Arrays.asList("zh-cn", "en", "ja"));
    private static final Set<String> SUPPORTED_THEMES =
            new HashSet<>(Arrays.asList("light", "dark", "eye-care"));

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private I18nUtil i18nUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private AvatarStorageService avatarStorageService;

    // ============================================================
    // 登录：BCrypt 密码校验
    // 兼容逻辑：若数据库存的是明文（老数据），自动升级为密文并回写
    // ============================================================
    @Override
    public Map<String, Object> login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (user == null) {
            throw new BusinessException(401, i18nUtil.getMessage("user.login.error"));
        }
        // 先判断是否为 BCrypt 密文，若是则用 matches 校验；若不是则比对明文并自动升级
        String dbPwd = user.getPassword();
        boolean isMatch;
        if (isBcryptPassword(dbPwd)) {
            // 标准 BCrypt 密文
            isMatch = passwordEncoder.matches(password, dbPwd);
        } else {
            // 明文兼容：比对原始明文，比对成功则升级为密文回写数据库
            isMatch = dbPwd != null && dbPwd.equals(password);
            if (isMatch) {
                int upgraded = userMapper.upgradeLegacyPassword(user.getId(), dbPwd, passwordEncoder.encode(password));
                if (upgraded != 1) {
                    throw new BusinessException(401, i18nUtil.getMessage("user.login.error"));
                }
            }
        }
        if (!isMatch) {
            throw new BusinessException(401, i18nUtil.getMessage("user.login.error"));
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getTokenVersion());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        // 返回用户语言偏好
        result.put("language", user.getLanguage() != null ? user.getLanguage() : "zh-cn");
        // 返回用户主题偏好
        result.put("theme", user.getTheme() != null ? user.getTheme() : "light");
        return result;
    }

    // ============================================================
    // 注册：密码加密后入库
    // ============================================================
    @Override
    public void register(String username, String password, String nickname) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
        if (count > 0) {
            throw new RuntimeException(i18nUtil.getMessage("user.register.exists"));
        }
        User user = new User();
        user.setUsername(username);
        // 密码 BCrypt 加密后入库
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        // 新注册用户默认语言
        user.setLanguage("zh-cn");
        // 新注册用户默认亮色主题
        user.setTheme("light");
        user.setTokenVersion(0);
        userMapper.insert(user);
    }

    @Override
    public UserProfileDTO getUserInfo(Long userId) {
        return UserProfileDTO.from(requireUser(userId));
    }

    @Override
    public void updateUserInfo(Long userId, String nickname, String avatar, String language, String theme) {
        if (avatar != null) {
            throw new BusinessException(400, "请通过头像上传或移除接口修改头像");
        }
        validateProfile(nickname, avatar, language, theme);
        User user = new User();
        user.setId(userId);
        user.setNickname(trimNickname(nickname));
        user.setLanguage(language);
        user.setTheme(theme);
        userMapper.updateById(user);
    }

    @Override
    public void updateUserProfile(Long userId, UpdateUserProfileDTO dto) {
        if (dto == null) {
            throw new BusinessException(400, i18nUtil.getMessage("validation.failed"));
        }
        requireUser(userId);
        updateUserInfo(userId, dto.getNickname(), null, dto.getLanguage(), dto.getTheme());
    }

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        String previousAvatar = user.getAvatar();
        AvatarStorageService.AvatarDeletion previousDeletion = avatarStorageService.prepareDeletion(userId, previousAvatar);
        String newAvatar = avatarStorageService.store(userId, file);
        try {
            User update = new User();
            update.setId(userId);
            update.setAvatar(newAvatar);
            int updated = userMapper.updateById(update);
            if (updated != 1) {
                throw new BusinessException(500, i18nUtil.getMessage("user.profile.update.error"));
            }
        } catch (RuntimeException e) {
            avatarStorageService.deleteManagedAvatar(userId, newAvatar);
            throw e;
        }
        avatarStorageService.deleteManagedAvatar(previousDeletion);
        return newAvatar;
    }

    @Override
    public void clearAvatar(Long userId) {
        User user = requireUser(userId);
        String previousAvatar = user.getAvatar();
        AvatarStorageService.AvatarDeletion previousDeletion = avatarStorageService.prepareDeletion(userId, previousAvatar);
        int updated = userMapper.clearAvatar(userId);
        if (updated != 1) {
            throw new BusinessException(500, i18nUtil.getMessage("user.profile.update.error"));
        }
        avatarStorageService.deleteManagedAvatar(previousDeletion);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, i18nUtil.getMessage("user.profile.notFound"));
        }
        return user;
    }

    private void validateProfile(String nickname, String avatar, String language, String theme) {
        if (nickname != null && nickname.trim().length() > 50) {
            throw new BusinessException(400, i18nUtil.getMessage("user.nickname.length"));
        }
        if (language != null && !SUPPORTED_LANGUAGES.contains(language)) {
            throw new BusinessException(400, i18nUtil.getMessage("user.language.unsupported"));
        }
        if (theme != null && !SUPPORTED_THEMES.contains(theme)) {
            throw new BusinessException(400, i18nUtil.getMessage("user.theme.unsupported"));
        }
    }

    private String trimNickname(String nickname) {
        return nickname == null ? null : nickname.trim();
    }

    // ============================================================
    // 修改密码：原密码 BCrypt matches 校验，新密码 encode 后入库
    // ============================================================
    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException(i18nUtil.getMessage("user.login.error"));
        }
        String dbPwd = user.getPassword();
        boolean oldMatch;
        if (isBcryptPassword(dbPwd)) {
            oldMatch = passwordEncoder.matches(oldPassword, dbPwd);
        } else {
            oldMatch = dbPwd != null && dbPwd.equals(oldPassword);
        }
        if (!oldMatch) {
            throw new RuntimeException(i18nUtil.getMessage("user.password.old.error"));
        }
        // 条件更新防止并发改密覆盖，并在同一条 SQL 中使已有令牌失效。
        int updated = userMapper.changePasswordAndInvalidateTokens(userId, dbPwd, passwordEncoder.encode(newPassword));
        if (updated != 1) {
            throw new BusinessException(409, "账号状态已变化，请重新登录后再修改密码");
        }
    }

    @Override
    public void deleteAccount(Long userId) {
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 逻辑删除用户
            userMapper.deleteById(userId);
        }
    }

    private boolean isBcryptPassword(String password) {
        return password != null && password.startsWith("$2");
    }
}
