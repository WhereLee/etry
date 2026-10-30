package com.wherelee.etry.user;

import com.wherelee.etry.common.BusinessException;
import com.wherelee.etry.common.ErrorCode;
import com.wherelee.etry.config.EtryProperties;
import com.wherelee.etry.file.ObjectKeys;
import com.wherelee.etry.storage.PresignTicket;
import com.wherelee.etry.storage.StorageGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> AVATAR_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageGateway storageGateway;
    private final EtryProperties properties;

    public User get(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public UserView view(User user) {
        String avatarUrl = user.getAvatarKey() == null
                ? null
                : storageGateway.presignDownload(user.getAvatarKey(), "avatar", null, Duration.ofHours(1));
        return new UserView(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getBio(),
                avatarUrl,
                user.getQuotaBytes(),
                user.getUsedBytes(),
                user.getReservedBytes(),
                user.getCreatedAt());
    }

    @Transactional
    public User updateProfile(Long userId, String nickname, String bio) {
        User user = get(userId);
        user.setNickname(nickname.trim());
        user.setBio(bio == null ? null : bio.trim());
        return userRepository.save(user);
    }

    /**
     * 改密后自增 token_version，把其它设备上的登录态全部踢掉，
     * 调用方需要用返回值重新签发当前这个 cookie。
     */
    @Transactional
    public User changePassword(Long userId, String oldPassword, String newPassword) {
        User user = get(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        return userRepository.save(user);
    }

    public AvatarPresign presignAvatar(Long userId, long sizeBytes, String contentType) {
        if (sizeBytes <= 0 || sizeBytes > properties.getAvatar().getMaxBytes()) {
            throw new BusinessException(ErrorCode.FILE_SIZE_INVALID, "头像大小不能超过 5MB");
        }
        if (!AVATAR_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID, "头像只支持 jpg/png/gif/webp");
        }
        String objectKey = ObjectKeys.avatar(userId);
        PresignTicket ticket = storageGateway.presignUpload(objectKey, properties.getAvatar().getMaxBytes(),
                contentType, Duration.ofSeconds(properties.getUpload().getPresignExpireSeconds()));
        return new AvatarPresign(objectKey, ticket);
    }

    /**
     * 确认头像上传：先校验对象确实存在且 key 属于当前用户，
     * 再更新指针，最后删掉旧对象，避免越换越多。
     */
    @Transactional
    public User commitAvatar(Long userId, String objectKey) {
        String expectedPrefix = "u/" + userId + "/avatar/";
        if (!objectKey.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        long size = storageGateway.stat(objectKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.OBJECT_NOT_FOUND)).sizeBytes();
        if (size <= 0 || size > properties.getAvatar().getMaxBytes()) {
            storageGateway.delete(objectKey);
            throw new BusinessException(ErrorCode.FILE_SIZE_INVALID, "头像大小不能超过 5MB");
        }

        User user = get(userId);
        String oldKey = user.getAvatarKey();
        user.setAvatarKey(objectKey);
        userRepository.save(user);
        if (oldKey != null && !oldKey.equals(objectKey)) {
            storageGateway.delete(oldKey);
        }
        return user;
    }

    @Transactional
    public User deleteAvatar(Long userId) {
        User user = get(userId);
        String oldKey = user.getAvatarKey();
        user.setAvatarKey(null);
        userRepository.save(user);
        if (oldKey != null) {
            storageGateway.delete(oldKey);
        }
        return user;
    }
}
