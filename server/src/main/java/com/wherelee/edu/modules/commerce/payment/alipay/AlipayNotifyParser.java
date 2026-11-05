package com.wherelee.edu.modules.commerce.payment.alipay;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析支付宝异步通知的原始表单字节。
 * 不走 request.getParameterMap()，避免容器按固定编码解码后与支付宝实际编码不一致导致验签失败：
 * 先从 ASCII 安全的原始字节里取出 charset，再用该 charset 解码全部参数。
 */
public final class AlipayNotifyParser {

    private static final Pattern CHARSET = Pattern.compile("(?:^|&)charset=([^&]+)");

    private AlipayNotifyParser() {
    }

    public static Map<String, String> parse(byte[] raw) {
        String charset = detectCharset(raw);
        String body = new String(raw, Charset.forName(charset));
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            params.put(URLDecoder.decode(key, Charset.forName(charset)),
                    URLDecoder.decode(value, Charset.forName(charset)));
        }
        return params;
    }

    private static String detectCharset(byte[] raw) {
        String ascii = new String(raw, StandardCharsets.ISO_8859_1);
        Matcher matcher = CHARSET.matcher(ascii);
        if (matcher.find()) {
            String candidate = URLDecoder.decode(matcher.group(1), StandardCharsets.ISO_8859_1).trim();
            try {
                if (!candidate.isEmpty() && Charset.isSupported(candidate)) {
                    return candidate;
                }
            } catch (IllegalArgumentException ignored) {
                // 非法 charset 名称，回退 UTF-8
            }
        }
        return "UTF-8";
    }
}
