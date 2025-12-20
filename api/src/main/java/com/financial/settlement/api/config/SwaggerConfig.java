package com.financial.settlement.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Server"),
                        new Server().url("http://localhost:8080").description("Docker Server")
                ))
                .components(new Components()
                        .addSecuritySchemes("Idempotency-Key", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Idempotency-Key")
                                .description("중복 결제 방지를 위한 멱등성 키")
                        )
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 인증 토큰")
                        )
                )
                .addSecurityItem(new SecurityRequirement().addList("Bearer"));
    }

    private Info apiInfo() {
        return new Info()
                .title("PayFlow API")
                .description("""
                        ## PayFlow 금융 정산 시스템 API

                        ### 주요 기능
                        - **결제 처리**: 결제 생성, 조회, 취소
                        - **정산 관리**: 청산, 정산, 대사
                        - **가맹점 관리**: 가맹점 CRUD, 수수료 정책

                        ### 인증
                        - 현재는 개발 환경으로 인증이 비활성화되어 있습니다.
                        - 추후 JWT 기반 인증이 적용될 예정입니다.

                        ### 멱등성
                        - 결제 생성 시 `Idempotency-Key` 헤더가 필수입니다.
                        - 동일한 키로 재요청 시 기존 결과를 반환합니다.
                        """)
                .version("v1.0.0")
                .contact(new Contact()
                        .name("PayFlow Team")
                        .email("support@payflow.com")
                )
                .license(new License()
                        .name("Private")
                );
    }
}
