package com.wherelee.edu.user;

import com.wherelee.edu.common.BizException;
import com.wherelee.edu.common.ErrorCode;
import com.wherelee.edu.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 头像的校验与替换。
 *
 * 从 UserProfileService 里单独拿出来的原因：这里的逻辑和「改昵称/简介」没有任何关系，
 * 只跟文件存储打交道。以后换存储实现、加图片压缩、加多尺寸缩略图，
 * 改动都收在这个类里，不会碰到资料那部分。
 *
 * 刻意不加 @Transactional：store / delete 是跨网 RPC（COS 上传与删除），
 * 包在事务里会一直占着数据库连接，COS 一慢就把连接池（20 个）拖垮。
 * 而 updateAvatar 只有一条语句，本身就是原子的。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final long MAX_AVATAR_BYTES = 2 * 1024 * 1024L;
    private static final String AVATAR_DIR = "avatar";

    /** 只接受这几种图片类型，扩展名由内容类型决定，不用原始文件名 */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );

    private final UserMapper userMapper;
    private final FileStorage fileStorage;

    /**
     * 校验并替换头像，返回新的对象键。
     *
     * 顺序是「先写新再删旧」：中途失败最多在对象存储里留一个孤儿文件，
     * 不会出现用户头像指向一个已被删掉的文件。
     */
    public String replace(User user, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAM, "头像不能超过 2MB");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : ALLOWED_TYPES.get(contentType.toLowerCase());
        if (extension == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只支持 jpg / png / webp 格式");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败");
        }

        // Content-Type 是客户端自报的，改个请求头就能把任意内容存成 .png。
        // 这里看真实文件头，与声明的格式对不上就拒绝。
        if (!matchesMagic(content, extension)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "文件内容与声明的图片格式不符");
        }

        String newKey = fileStorage.store(content, extension, AVATAR_DIR);
        String oldKey = user.getAvatarKey();
        userMapper.updateAvatar(user.getId(), newKey);
        if (oldKey != null && !oldKey.isBlank()) {
            fileStorage.delete(oldKey);
        }
        log.info("用户更新头像 userId={} key={}", user.getId(), newKey);
        return newKey;
    }

    /**
     * 按文件头（魔数）判断真实格式。比 ImageIO 解码宽松：
     * JDK 自带的 ImageIO 不认 webp，用它会误杀合法图片。
     */
    static boolean matchesMagic(byte[] c, String extension) {
        if (c == null || c.length < 12) {
            return false;
        }
        return switch (extension) {
            case "jpg" -> (c[0] & 0xFF) == 0xFF && (c[1] & 0xFF) == 0xD8 && (c[2] & 0xFF) == 0xFF;
            case "png" -> (c[0] & 0xFF) == 0x89 && c[1] == 'P' && c[2] == 'N' && c[3] == 'G';
            case "webp" -> c[0] == 'R' && c[1] == 'I' && c[2] == 'F' && c[3] == 'F'
                    && c[8] == 'W' && c[9] == 'E' && c[10] == 'B' && c[11] == 'P';
            default -> false;
        };
    }
}
