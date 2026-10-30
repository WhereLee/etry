package com.wherelee.edu.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 把方法参数转成可读的审计详情，并抹掉密码。
 *
 * 单独成类的原因很直接：脱敏正则出过一次漏（不认 JSON 的 \" 转义，
 * 密码里带引号就会漏出后半段明文）。放在切面私有方法里没法单测，
 * 挪出来后可以对着字符串单独验证。
 */
@Component
@RequiredArgsConstructor
public class AuditDetailFormatter {

    /**
     * 密码类字段一律替掉值，只留字段名。
     * 值部分用 (?:\\.|[^"\\])* 而不是 [^"]*：JSON 里双引号会被转义成 \"，
     * 不认转义会在引号处提前结束匹配，把后半段密码漏成明文。
     */
    private static final Pattern PASSWORD_FIELD = Pattern.compile(
            "(\"[A-Za-z]*[Pp]assword[A-Za-z]*\"\\s*:\\s*)\"(?:\\\\.|[^\"\\\\])*\"");

    private final ObjectMapper objectMapper;

    public String format(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        return Arrays.stream(args)
                .map(this::describe)
                .collect(Collectors.joining(", "));
    }

    /** 暴露给单元测试，避免为了验证脱敏而构造整个切面 */
    public String mask(String json) {
        return PASSWORD_FIELD.matcher(json).replaceAll("$1\"***\"");
    }

    private String describe(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof CharSequence || arg instanceof Number || arg instanceof Boolean) {
            return String.valueOf(arg);
        }
        if (arg instanceof MultipartFile file) {
            return "file:" + file.getOriginalFilename() + "(" + file.getSize() + "B)";
        }
        try {
            return mask(objectMapper.writeValueAsString(arg));
        } catch (Exception e) {
            // 序列化失败（循环引用、无 getter）时退回类名，不能因为取不到值就丢掉整条审计
            return arg.getClass().getSimpleName();
        }
    }
}
