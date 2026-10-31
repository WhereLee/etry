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
 * 图片上传。只管「校验 + 存对象存储」，不关心这张图属于谁。
 *
 * 用户头像和讲师头像是两张不同的表，共用这里的校验与存储；
 * 具体写哪张表、什么时候删旧图，由调用方决定。
 * 这样加第三种图片（比如课程封面）时不用改这个类。
 *
 * 刻意不加 @Transactional：store / delete 是跨网 RPC，包在事务里会占着数据库连接。
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

    private final FileStorage fileStorage;

    /**
     * 校验并存入对象存储，返回对象键。调用方负责把键写进自己的表。
     *
     * 顺序上应该「先存新、再改库、最后删旧」：中途失败最多在对象存储里留一个孤儿文件，
     * 不会出现头像指向一个已被删掉的文件。
     */
    public String store(MultipartFile file) {
        return store(file, AVATAR_DIR);
    }

    /**
     * 同规格图片（课程封面）也走这里，只是目录不同。
     * 校验规则完全一致：2MB、JPG/PNG/WebP、看真实文件头而不是声明的类型。
     */
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BizException(ErrorCode.INVALID_PARAM, "图片不能超过 2MB");
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

        String key = fileStorage.store(content, extension, directory);
        log.info("图片已存入对象存储 key={} size={}", key, content.length);
        return key;
    }

    /** 删旧图。对象已不存在或存储侧报错都不影响主流程 */
    public void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        fileStorage.delete(key);
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
