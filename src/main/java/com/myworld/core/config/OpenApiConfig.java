package com.myworld.core.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * FIX 5: Swagger / OpenAPI documentation.
 *
 * Access in dev: http://localhost:8080/swagger-ui/index.html
 * Raw JSON:      http://localhost:8080/v3/api-docs  (import into Postman)
 *
 * Disabled in prod via application-prod.yml (springdoc.swagger-ui.enabled=false)
 *
 * React Native team: click "Authorize" in Swagger UI, paste JWT token, test all endpoints.
 * React Admin team:  use openapi-typescript-codegen on /v3/api-docs to auto-generate typed client.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Value("${app.api.dev-server-url:http://localhost:8080}")
    private String devServerUrl;

    @Value("${app.api.prod-server-url:https://api.earnx.app}")
    private String prodServerUrl;

    @Bean
    public OpenAPI earnXOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EarnX API")
                        .version("v1.0")
                        .description("""
                                EarnX3 backend — rewards, referrals, payouts, campaigns, spin wheel.

                                **Auth:** All endpoints except /api/auth/** require a JWT.
                                Click **Authorize** above and paste your token (no 'Bearer' prefix needed).

                                **Versioning:** Use /api/v1/* in all new code (React Native, React Admin).
                                Legacy /api/* still works for backward compatibility.
                                """)
                        .contact(new Contact().name("EarnX Dev Team").email("dev@earnx.app"))
                        .license(new License().name("Private")))
                .servers(List.of(
                        new Server().url(devServerUrl).description("Local development"),
                        new Server().url(prodServerUrl).description("Production")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from POST /api/v1/auth/login")));
    }
}
