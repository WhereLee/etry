package com.wherelee.edu.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 头像魔数校验的单测。
 * 这段逻辑从 UserProfileService 挪到 AvatarService 后才有地方单独验证，
 * 否则只能真起服务、真发 multipart 请求。
 */
class AvatarServiceMagicTest {

    @Test
    @DisplayName("标准 PNG 文件头通过")
    void shouldAcceptPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(AvatarService.matchesMagic(png, "png")).isTrue();
    }

    @Test
    @DisplayName("标准 JPEG 文件头通过")
    void shouldAcceptJpeg() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertThat(AvatarService.matchesMagic(jpg, "jpg")).isTrue();
    }

    @Test
    @DisplayName("WebP 的 RIFF....WEBP 通过")
    void shouldAcceptWebp() {
        byte[] webp = {'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P'};
        assertThat(AvatarService.matchesMagic(webp, "webp")).isTrue();
    }

    @Test
    @DisplayName("文本伪装成 PNG 被拒")
    void shouldRejectTextAsPng() {
        byte[] text = "this is not an image".getBytes(StandardCharsets.UTF_8);
        assertThat(AvatarService.matchesMagic(text, "png")).isFalse();
    }

    @Test
    @DisplayName("PNG 内容但声明成 jpg 被拒")
    void shouldRejectMismatchedExtension() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(AvatarService.matchesMagic(png, "jpg")).isFalse();
    }

    @Test
    @DisplayName("内容太短不足以判断时被拒")
    void shouldRejectTooShort() {
        assertThat(AvatarService.matchesMagic(new byte[]{1, 2, 3}, "png")).isFalse();
        assertThat(AvatarService.matchesMagic(null, "png")).isFalse();
    }
}
