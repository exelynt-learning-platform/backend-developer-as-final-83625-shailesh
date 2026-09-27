package com.example.booking.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI bookingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resource Booking System API")
                        .version("1.0.0")
                        .description("""
                                REST API for managing resources and reservations.

                                **How to authenticate:** call `POST /auth/login`, copy the `token` from the \
                                response, click **Authorize** and paste it (without the "Bearer " prefix).

                                **Seed users:** `admin / admin123` (ADMIN), `user1 / user123` and \
                                `user2 / user123` (USER)."""))
                .components(new Components().addSecuritySchemes(SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME_NAME)); // applies to all endpoints
    }
}