package com.wherelee.edu.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档。
 *
 * 文档地址：/swagger-ui.html，原始 JSON：/v3/api-docs
 * 两个都在免登录白名单里——文档本身不带业务数据，靠 Bearer token 才能调通接口。
 *
 * 生产环境要关就在启动时设 API_DOCS_ENABLED=false。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI eduOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("网课平台 API")
                        .version("1.0")
                        .description("""
                                免费录播课平台后端。统一返回体为 {code, message, data, timestamp}，成功时 code=0。

                                鉴权：先调 /api/auth/login 拿 token，再点右上角 Authorize 填进去；
                                课程列表和详情不用登录，播放、学习、评价、收藏、讲师接口都要。

                                讲师首次登录会被要求改密（mustChangePassword=true），
                                改完后旧 token 立即失效，需要重新登录。"""))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
