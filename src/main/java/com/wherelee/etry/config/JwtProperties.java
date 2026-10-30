package com.wherelee.etry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "etry.jwt")
public class JwtProperties {

    /** 签名密钥，生产必须由环境变量提供，长度不少于 32 字节 */
    private String secret;

    /** 有效期天数 */
    private int expireDays = 7;

    /** 承载 token 的 cookie 名 */
    private String cookieName = "etry_token";

    /** 走 HTTPS 后置为 true，本地 HTTP 环境必须为 false，否则 cookie 不会被下发 */
    private boolean cookieSecure = false;
}
