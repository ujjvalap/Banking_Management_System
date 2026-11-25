package com.sprint.bank.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Northstar Bank — Banking Management System API")
                        .version("1.0.0")
                        .description("REST API for customer onboarding, JWT authentication with refresh rotation, "
                                + "account management, atomic deposits/withdrawals/transfers, statements, and admin oversight.")
                        .contact(new Contact().name("Northstar Engineering").email("engineering@northstarbank.local"))
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Provide the JWT access token obtained from /api/auth/login or /api/auth/register")));
    }
}
