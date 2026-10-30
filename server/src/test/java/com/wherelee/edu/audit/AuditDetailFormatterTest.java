package com.wherelee.edu.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 纯单元测试，不启动 Spring。
 *
 * 之所以能这么写，是因为脱敏逻辑已经从切面里挪出来了：
 * 以前它藏在私有方法里，只能起服务发请求才能验证。
 */
class AuditDetailFormatterTest {

    private final AuditDetailFormatter formatter = new AuditDetailFormatter(new ObjectMapper());

    @Test
    @DisplayName("普通密码字段替换成 ***")
    void shouldMaskPlainPassword() {
        String json = "{\"username\":\"admin\",\"password\":\"admin123\"}";

        String masked = formatter.mask(json);

        assertThat(masked).contains("\"password\":\"***\"");
        assertThat(masked).doesNotContain("admin123");
        assertThat(masked).contains("\"username\":\"admin\"");
    }

    @Test
    @DisplayName("密码里带双引号也不会漏出后半段")
    void shouldMaskPasswordWithEscapedQuote() {
        // Jackson 会把 pa"ss123 序列化成 pa\"ss123
        String json = "{\"username\":\"u\",\"password\":\"pa\\\"ss123\"}";

        String masked = formatter.mask(json);

        assertThat(masked).isEqualTo("{\"username\":\"u\",\"password\":\"***\"}");
        assertThat(masked).doesNotContain("ss123");
    }

    @Test
    @DisplayName("oldPassword / newPassword 这类驼峰字段同样处理")
    void shouldMaskCamelCasePasswordFields() {
        String json = "{\"oldPassword\":\"aaa111\",\"newPassword\":\"bbb222\"}";

        String masked = formatter.mask(json);

        assertThat(masked).doesNotContain("aaa111").doesNotContain("bbb222");
        assertThat(masked).isEqualTo("{\"oldPassword\":\"***\",\"newPassword\":\"***\"}");
    }

    @Test
    @DisplayName("没有密码字段的 JSON 原样保留")
    void shouldKeepJsonWithoutPassword() {
        String json = "{\"status\":\"DISABLED\"}";

        assertThat(formatter.mask(json)).isEqualTo(json);
    }

    @Test
    @DisplayName("空参数返回 null，基本类型直接取字符串")
    void shouldFormatArgs() {
        assertThat(formatter.format(new Object[0])).isNull();
        assertThat(formatter.format(new Object[]{12L, "text", null})).isEqualTo("12, text, null");
    }
}
