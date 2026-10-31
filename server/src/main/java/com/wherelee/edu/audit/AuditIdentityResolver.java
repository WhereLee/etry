package com.wherelee.edu.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.edu.security.UserContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 确定「这条审计是谁干的」。
 *
 * 绝大多数接口能从 UserContext 直接拿到。麻烦的是登录这类白名单接口：
 * 切面执行时 UserContext 还是空的，只能从入参（含 username 的请求体）
 * 或返回值（含 userId/username 的响应）里找，否则操作人两列恒为 null，
 * 而审计查询恰好按 userId 过滤，「按人查爆破记录」这个入口就用不了。
 */
@Component
@RequiredArgsConstructor
public class AuditIdentityResolver {

    private final ObjectMapper objectMapper;

    /**
     * 先用 UserContext，缺什么再按 参数 → 返回值 的顺序补。
     * 返回值放在后面：登录成功时它才带 userId。
     */
    public void fill(AuditRecord record, Object[] args, Object result) {
        UserContext.Principal principal = UserContext.get();
        if (principal != null) {
            record.setUserId(principal.userId());
            record.setUsername(principal.username());
        }
        if (record.getUsername() == null) {
            record.setUsername(findText(args, "username"));
        }
        if (record.getUserId() == null) {
            record.setUserId(findLong(result, "userId"));
        }
        if (record.getUsername() == null) {
            record.setUsername(findText(result, "username"));
        }
    }

    private String findText(Object source, String field) {
        if (source instanceof Object[] args) {
            for (Object arg : args) {
                String value = findText(arg, field);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        JsonNode node = toObjectNode(source);
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private Long findLong(Object source, String field) {
        if (source instanceof Object[] args) {
            for (Object arg : args) {
                Long value = findLong(arg, field);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        JsonNode node = toObjectNode(source);
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.asLong();
    }

    /** 把 ApiResponse 的 data 层剥出来；文件类参数不参与，避免把字节数组序列化 */
    private JsonNode toObjectNode(Object value) {
        if (value == null || value instanceof byte[] || value instanceof MultipartFile
                || value instanceof ServletRequest || value instanceof ServletResponse) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            if (node == null || !node.isObject()) {
                return null;
            }
            JsonNode data = node.get("data");
            return data != null && data.isObject() ? data : node;
        } catch (Exception e) {
            return null;
        }
    }
}
