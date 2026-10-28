package com.finance.service;

import com.finance.common.exception.BusinessException;
import com.finance.common.i18n.I18nUtil;
import com.finance.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 管理用户头像文件的校验、保存与安全删除。
 */
@Service
public class AvatarStorageService {

    public static final String PUBLIC_PATH_PREFIX = "/uploads/avatars/";
    private static final Set<String> ALLOWED_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png"));
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            new HashSet<>(Arrays.asList("image/jpeg", "image/png"));
    private static final int MAX_IMAGE_DIMENSION = 4096;

    private final Path storageRoot;
    private final long maxFileSize;
    private final I18nUtil i18nUtil;
    private final UserMapper userMapper;

    public AvatarStorageService(
            @Value("${app.avatar.storage-dir:./uploads/avatars}") String storageDir,
            @Value("${app.avatar.max-file-size:2097152}") long maxFileSize,
            I18nUtil i18nUtil,
            UserMapper userMapper) {
        this.storageRoot = Paths.get(storageDir).toAbsolutePath().normalize();
        this.maxFileSize = maxFileSize;
        this.i18nUtil = i18nUtil;
        this.userMapper = userMapper;
    }

    /**
     * 校验并保存头像，返回写入数据库的相对访问路径。
     */
    public String store(Long userId, MultipartFile file) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "请先登录");
        }
        String extension = validate(file);
        try {
            Files.createDirectories(storageRoot);
            String filename = "u" + userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = resolveManagedFile(filename);
            Path temporary = Files.createTempFile(storageRoot, ".avatar-", ".tmp");
            try {
                try (InputStream inputStream = file.getInputStream()) {
                    Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                moveIntoPlace(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return PUBLIC_PATH_PREFIX + filename;
        } catch (IOException e) {
            throw new BusinessException(500, message("user.avatar.store.error", "头像保存失败，请稍后重试"));
        }
    }

    /**
     * 在数据库资料更新前核实归属，更新成功后才使用返回凭据清理文件。
     */
    public AvatarDeletion prepareDeletion(Long userId, String avatarPath) {
        String filename = managedFilename(avatarPath);
        if (userId == null || userId <= 0 || filename == null) {
            return null;
        }
        boolean ownFile = filename.matches("u" + userId + "-[a-f0-9]{32}\\.(jpg|png)");
        boolean legacyFile = filename.matches("[a-f0-9]{32}\\.(jpg|png)");
        if (ownFile || (legacyFile && userMapper.isExclusiveAvatarOwner(userId, avatarPath))) {
            return new AvatarDeletion(resolveManagedFile(filename));
        }
        return null;
    }

    /** 新文件写入数据库失败时，仅回收当前用户刚生成的文件。 */
    public void deleteManagedAvatar(Long userId, String avatarPath) {
        String filename = managedFilename(avatarPath);
        if (userId != null && userId > 0 && filename != null
                && filename.matches("u" + userId + "-[a-f0-9]{32}\\.(jpg|png)")) {
            deleteManagedAvatar(new AvatarDeletion(resolveManagedFile(filename)));
        }
    }

    public void deleteManagedAvatar(AvatarDeletion deletion) {
        if (deletion == null) return;
        try {
            Files.deleteIfExists(deletion.path);
        } catch (IOException ignored) {
            // 资料更新已经成功时，旧文件清理失败不应影响用户操作。
        }
    }

    private String managedFilename(String avatarPath) {
        if (avatarPath == null || !avatarPath.startsWith(PUBLIC_PATH_PREFIX)) return null;
        String filename = avatarPath.substring(PUBLIC_PATH_PREFIX.length());
        if (filename.isEmpty() || filename.contains("/") || filename.contains("\\")) return null;
        return filename;
    }

    public static class AvatarDeletion {
        private final Path path;

        private AvatarDeletion(Path path) {
            this.path = path;
        }
    }

    public String getResourceLocation() {
        String location = storageRoot.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, message("user.avatar.empty", "请选择头像文件"));
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(400, message("user.avatar.size", "头像文件不能超过2MB"));
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extensionOf(originalFilename);
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(400, message("user.avatar.type", "仅支持JPG和PNG图片"));
        }

        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String actualExtension = normalizeFormat(format);
                if (actualExtension == null || !matchesExtension(extension, actualExtension)) {
                    throw invalidImage();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION) {
                    throw new BusinessException(400, message("user.avatar.dimension", "头像尺寸不能超过4096×4096"));
                }
                // 完整解码一次，避免仅有合法图片头但正文损坏的文件被保存。
                if (reader.read(0) == null) {
                    throw invalidImage();
                }
                return actualExtension;
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw invalidImage();
        }
    }

    private Path resolveManagedFile(String filename) {
        Path target = storageRoot.resolve(filename).normalize();
        if (!storageRoot.equals(target.getParent())) {
            throw new BusinessException(400, message("user.avatar.invalid", "头像文件无效"));
        }
        return target;
    }

    private void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeFormat(String format) {
        if ("jpeg".equals(format) || "jpg".equals(format)) {
            return "jpg";
        }
        if ("png".equals(format)) {
            return "png";
        }
        return null;
    }

    private boolean matchesExtension(String extension, String actualExtension) {
        if ("jpg".equals(actualExtension)) {
            return "jpg".equals(extension) || "jpeg".equals(extension);
        }
        return actualExtension.equals(extension);
    }

    private BusinessException invalidImage() {
        return new BusinessException(400, message("user.avatar.invalid", "头像文件无效或已损坏"));
    }

    private String message(String key, String fallback) {
        String value = i18nUtil != null ? i18nUtil.getMessage(key) : null;
        return value == null || key.equals(value) ? fallback : value;
    }

}
