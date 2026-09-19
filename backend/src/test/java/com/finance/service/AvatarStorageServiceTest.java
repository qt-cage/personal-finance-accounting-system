package com.finance.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AvatarStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndDeletesValidPng() throws Exception {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = pngFile("avatar.png", 80, 80);

        String avatarPath = service.store(1L, file);
        Path stored = tempDir.resolve(avatarPath.substring(AvatarStorageService.PUBLIC_PATH_PREFIX.length()));

        assertTrue(avatarPath.startsWith(AvatarStorageService.PUBLIC_PATH_PREFIX + "u1-"));
        assertTrue(Files.exists(stored));

        service.deleteManagedAvatar(1L, avatarPath);
        assertFalse(Files.exists(stored));
    }

    @Test
    void storesValidJpeg() throws Exception {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpeg", "image/jpeg", jpegBytes(80, 80));

        String avatarPath = service.store(1L, file);

        assertTrue(avatarPath.endsWith(".jpg"));
        assertTrue(Files.exists(tempDir.resolve(
                avatarPath.substring(AvatarStorageService.PUBLIC_PATH_PREFIX.length()))));
    }

    @Test
    void rejectsEmptyFile() {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[0]);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.store(1L, file));
        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsOversizedFile() {
        AvatarStorageService service = createService(4L);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[5]);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.store(1L, file));
        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsDisguisedImage() {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", "not-an-image".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.store(1L, file));
        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsUnsupportedExtension() throws Exception {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.gif", "image/gif", pngBytes(80, 80));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.store(1L, file));
        assertEquals(400, exception.getCode());
    }

    @Test
    void rejectsImageDimensionsAboveLimit() throws Exception {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        MockMultipartFile file = new MockMultipartFile(
                "file", "wide.png", "image/png", pngBytes(4097, 1));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.store(1L, file));
        assertEquals(400, exception.getCode());
    }

    private AvatarStorageService createService(long maxSize) {
        return new AvatarStorageService(tempDir.toString(), maxSize, mock(I18nUtil.class), mock(UserMapper.class));
    }

    @Test
    void cannotDeleteAnotherUsersAvatarEvenWhenItsPathIsKnown() throws Exception {
        AvatarStorageService service = createService(2 * 1024 * 1024L);
        String otherAvatar = service.store(2L, pngFile("avatar.png", 8, 8));
        Path stored = tempDir.resolve(otherAvatar.substring(AvatarStorageService.PUBLIC_PATH_PREFIX.length()));

        service.deleteManagedAvatar(1L, otherAvatar);
        service.deleteManagedAvatar(service.prepareDeletion(1L, otherAvatar));

        assertTrue(Files.exists(stored));
    }

    @Test
    void keepsLegacyAvatarUnlessDatabaseProvesExclusiveOwnership() throws Exception {
        UserMapper mapper = mock(UserMapper.class);
        AvatarStorageService service = new AvatarStorageService(tempDir.toString(), 1024L, mock(I18nUtil.class), mapper);
        String filename = "0123456789abcdef0123456789abcdef.png";
        String avatarPath = AvatarStorageService.PUBLIC_PATH_PREFIX + filename;
        Path stored = Files.write(tempDir.resolve(filename), pngBytes(8, 8));

        service.deleteManagedAvatar(1L, avatarPath);
        service.deleteManagedAvatar(service.prepareDeletion(1L, avatarPath));
        assertTrue(Files.exists(stored));

        when(mapper.isExclusiveAvatarOwner(1L, avatarPath)).thenReturn(true);
        AvatarStorageService.AvatarDeletion verified = service.prepareDeletion(1L, avatarPath);
        // 资料写入后原引用不再存在，清理仍依据写入前已核实的归属凭据。
        when(mapper.isExclusiveAvatarOwner(1L, avatarPath)).thenReturn(false);
        service.deleteManagedAvatar(verified);
        assertFalse(Files.exists(stored));
    }

    @Test
    void keepsUnknownManagedFilesAndRejectsTraversal() throws Exception {
        AvatarStorageService service = createService(1024L);
        Path unknown = Files.write(tempDir.resolve("unknown.png"), pngBytes(8, 8));
        service.deleteManagedAvatar(service.prepareDeletion(1L, AvatarStorageService.PUBLIC_PATH_PREFIX + "unknown.png"));
        service.deleteManagedAvatar(1L, AvatarStorageService.PUBLIC_PATH_PREFIX + "../unknown.png");
        service.deleteManagedAvatar(service.prepareDeletion(1L, AvatarStorageService.PUBLIC_PATH_PREFIX + "../unknown.png"));
        assertTrue(Files.exists(unknown));
    }

    @Test
    void rejectsUploadWithoutValidUser() throws Exception {
        AvatarStorageService service = createService(1024L);
        MockMultipartFile file = pngFile("avatar.png", 8, 8);
        assertEquals(401, assertThrows(BusinessException.class, () -> service.store(null, file)).getCode());
        assertEquals(401, assertThrows(BusinessException.class, () -> service.store(0L, file)).getCode());
    }

    private MockMultipartFile pngFile(String filename, int width, int height) throws Exception {
        return new MockMultipartFile("file", filename, "image/png", pngBytes(width, height));
    }

    private byte[] pngBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] jpegBytes(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        return output.toByteArray();
    }
}
