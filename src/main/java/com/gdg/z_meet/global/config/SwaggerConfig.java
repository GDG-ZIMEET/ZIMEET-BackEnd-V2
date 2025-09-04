package com.gdg.z_meet.global.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.models.servers.Server;

@Configuration    // 스프링 실행시 설정파일 읽어들이기 위한 어노테이션
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {

        String accessTokenSchemeName = "accessToken";
        String refreshTokenSchemeName = "refreshToken";

        // API 요청헤더에 인증정보 포함
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(accessTokenSchemeName);

        // SecuritySchemes 등록
        Components components = new Components()
                .addSecuritySchemes(accessTokenSchemeName, new SecurityScheme()
                        .name("accessToken")
                        .type(SecurityScheme.Type.HTTP) // HTTP 방식
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addSecuritySchemes(refreshTokenSchemeName, new SecurityScheme()
                        .name("refreshToken")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"));

        return new OpenAPI()
                .components(components)
                .info(apiInfo())
                .addServersItem(new Server().url("/"))
                .addSecurityItem(securityRequirement);
    }

    private Info apiInfo() {
        return new Info()
                .title("GDG Z밋 Swagger")
                .description("GDG Z밋 Swagger 입니다.")
                .version("1.0.0");
    }
}