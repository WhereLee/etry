package com.wherelee.edu.user;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.storage.FileStorage;
import com.wherelee.edu.user.dto.UpdateProfileRequest;
import com.wherelee.edu.user.dto.UserProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 用户自助资料：查看、改昵称与简介、换头像。
 *
 * 头像的校验与落存储不在这里，在 {@link AvatarService}——
 * 那部分只跟文件和对象存储打交道，和资料本身没有关系。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final FileStorage fileStorage;
    private final AvatarService avatarService;

    public UserProfileVO get(Long userId) {
        return toVO(requireUser(userId));
    }

    @Transactional
    public void update(Long userId, UpdateProfileRequest request) {
        requireUser(userId);
        userMapper.updateProfile(userId, request.nickname().trim(), request.bio());
        log.info("用户更新资料 userId={}", userId);
    }

    /**
     * 换头像：先存新图、再改库、最后删旧图。
     * 中途失败最多在对象存储里留一个孤儿文件，不会出现头像指向已删文件。
     */
    public UserProfileVO uploadAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        String newKey = avatarService.store(file);
        String oldKey = user.getAvatarKey();
        userMapper.updateAvatar(userId, newKey);
        avatarService.deleteQuietly(oldKey);
        user.setAvatarKey(newKey);
        return toVO(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private UserProfileVO toVO(User user) {
        List<String> roles = roleMapper.findCodesByUserId(user.getId());
        return new UserProfileVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getBio(),
                fileStorage.url(user.getAvatarKey()),
                roles,
                user.getCreatedAt());
    }
}
