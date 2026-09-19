package com.finance.service.impl;

import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.common.util.JwtUtil;
import com.finance.entity.User;
import com.finance.entity.dto.UpdateUserProfileDTO;
import com.finance.entity.dto.UserProfileDTO;
import com.finance.mapper.UserMapper;
import com.finance.service.AvatarStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AvatarStorageService avatarStorageService;

    @Mock
    private I18nUtil i18nUtil;

    @Mock
    private JwtUtil jwtUtil;

    @Spy
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserServiceImpl service;

    @Test
    void rejectsUnsupportedLanguage() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateUserInfo(1L, null, null, "fr", null));
        assertEquals(400, ex.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void rejectsUnsupportedTheme() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateUserInfo(1L, null, null, null, "neon"));
        assertEquals(400, ex.getCode());
    }

    @Test
    void acceptsSupportedPreferences() {
        service.updateUserInfo(1L, "用户A", null, "ja", "eye-care");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals("ja", captor.getValue().getLanguage());
        assertEquals("eye-care", captor.getValue().getTheme());
        assertNull(captor.getValue().getTokenVersion());
    }

    @Test
    void returnsWhitelistedProfileDto() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setPassword("secret");
        user.setNickname("测试用户");
        user.setLanguage("zh-cn");
        user.setTheme("dark");
        when(userMapper.selectById(1L)).thenReturn(user);

        UserProfileDTO profile = service.getUserInfo(1L);

        assertEquals("tester", profile.getUsername());
        assertEquals("测试用户", profile.getNickname());
        assertEquals("dark", profile.getTheme());
        String json = new ObjectMapper().writeValueAsString(profile);
        assertFalse(json.contains("password"));
        assertFalse(json.contains("deleted"));
        assertFalse(json.contains("updateTime"));
    }

    @Test
    void trimsNicknameWhenUpdatingProfile() {
        User existing = new User();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);
        UpdateUserProfileDTO dto = new UpdateUserProfileDTO();
        dto.setNickname("  新昵称  ");

        service.updateUserProfile(1L, dto);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals("新昵称", captor.getValue().getNickname());
    }

    @Test
    void uploadsAvatarThenDeletesPreviousManagedFile() {
        User existing = new User();
        existing.setId(1L);
        existing.setAvatar("/uploads/avatars/old.png");
        when(userMapper.selectById(1L)).thenReturn(existing);
        AvatarStorageService.AvatarDeletion previousDeletion = mock(AvatarStorageService.AvatarDeletion.class);
        when(avatarStorageService.prepareDeletion(1L, existing.getAvatar())).thenReturn(previousDeletion);
        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1});
        when(avatarStorageService.store(1L, file)).thenReturn("/uploads/avatars/new.png");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        String result = service.uploadAvatar(1L, file);

        assertEquals("/uploads/avatars/new.png", result);
        verify(userMapper).updateById(any(User.class));
        verify(avatarStorageService).deleteManagedAvatar(previousDeletion);
    }

    @Test
    void removesNewFileWhenAvatarDatabaseUpdateFails() {
        User existing = new User();
        existing.setId(1L);
        when(userMapper.selectById(1L)).thenReturn(existing);
        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1});
        when(avatarStorageService.store(1L, file)).thenReturn("/uploads/avatars/new.png");
        when(userMapper.updateById(any(User.class))).thenThrow(new RuntimeException("数据库写入失败"));

        assertThrows(RuntimeException.class, () -> service.uploadAvatar(1L, file));

        verify(avatarStorageService).deleteManagedAvatar(1L, "/uploads/avatars/new.png");
        verify(avatarStorageService, never()).deleteManagedAvatar(any(AvatarStorageService.AvatarDeletion.class));
    }

    @Test
    void clearsAvatarAfterDatabaseUpdateSucceeds() {
        User existing = new User();
        existing.setId(1L);
        existing.setAvatar("/uploads/avatars/old.png");
        when(userMapper.selectById(1L)).thenReturn(existing);
        AvatarStorageService.AvatarDeletion previousDeletion = mock(AvatarStorageService.AvatarDeletion.class);
        when(avatarStorageService.prepareDeletion(1L, existing.getAvatar())).thenReturn(previousDeletion);
        when(userMapper.clearAvatar(1L)).thenReturn(1);

        service.clearAvatar(1L);

        verify(avatarStorageService).deleteManagedAvatar(previousDeletion);
    }

    @Test
    void keepsOldAvatarWhenClearDatabaseUpdateFails() {
        User existing = new User();
        existing.setId(1L);
        existing.setAvatar("/uploads/avatars/old.png");
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.clearAvatar(1L)).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.clearAvatar(1L));

        verify(avatarStorageService, never()).deleteManagedAvatar(any(AvatarStorageService.AvatarDeletion.class));
    }

    @Test
    void rejectsLegacyAvatarParameterIncludingEmptyValue() {
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.updateUserInfo(1L, null, "/uploads/avatars/other.png", null, null)).getCode());
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.updateUserInfo(1L, null, "", null, null)).getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void loginIssuesCurrentTokenVersionAndSupportsBcrypt2b() {
        User user = authenticationUser();
        user.setPassword(passwordEncoder.encode("password").replace("$2a$", "$2b$"));
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generateToken(1L, "tester", 3)).thenReturn("versioned-token");

        assertEquals("versioned-token", service.login("tester", "password").get("token"));
        verify(userMapper, never()).upgradeLegacyPassword(any(), any(), any());
    }

    @Test
    void successfulPasswordChangeUsesAtomicConditionalUpdate() {
        User user = authenticationUser();
        String oldHash = passwordEncoder.encode("old-password");
        user.setPassword(oldHash);
        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.changePasswordAndInvalidateTokens(eq(1L), eq(oldHash), any())).thenReturn(1);

        service.changePassword(1L, "old-password", "new-password");

        ArgumentCaptor<String> encodedPassword = ArgumentCaptor.forClass(String.class);
        verify(userMapper).changePasswordAndInvalidateTokens(eq(1L), eq(oldHash), encodedPassword.capture());
        assertTrue(passwordEncoder.matches("new-password", encodedPassword.getValue()));
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void wrongOldPasswordDoesNotInvalidateOrUpdateAnything() {
        User user = authenticationUser();
        user.setPassword(passwordEncoder.encode("old-password"));
        when(userMapper.selectById(1L)).thenReturn(user);
        assertThrows(RuntimeException.class, () -> service.changePassword(1L, "wrong", "new-password"));
        verify(userMapper, never()).changePasswordAndInvalidateTokens(any(), any(), any());
    }

    @Test
    void concurrentPasswordChangeCannotOverwriteNewerPassword() {
        User user = authenticationUser();
        user.setPassword(passwordEncoder.encode("old-password"));
        when(userMapper.selectById(1L)).thenReturn(user);
        assertEquals(409, assertThrows(BusinessException.class,
                () -> service.changePassword(1L, "old-password", "new-password")).getCode());
    }

    @Test
    void failedLegacyPasswordMigrationDoesNotIssueToken() {
        User user = authenticationUser();
        user.setPassword("old-password");
        when(userMapper.selectOne(any())).thenReturn(user);
        assertEquals(401, assertThrows(BusinessException.class,
                () -> service.login("tester", "old-password")).getCode());
        verify(jwtUtil, never()).generateToken(any(), any(), any());
    }

    private User authenticationUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("tester");
        user.setTokenVersion(3);
        user.setDeleted(0);
        return user;
    }
}
