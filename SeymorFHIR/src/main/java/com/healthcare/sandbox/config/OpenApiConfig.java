package com.healthcare.sandbox.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Seymour Regional EHR — Central FHIR R4 Developer Portal")
                        .version("1.0.0")
                        .description("Centralized API Gateway & Regional Developer Portal simulating British Columbia clinical systems, SMART-on-FHIR OAuth2, and Modulus-11 PHN check-digit validation.")
                        .contact(new Contact()
                                .name("Enterprise Health Integration Engineering Team")
                                .url("https://github.com/karthikaiyasamy/seymour-sandbox")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your SMART-on-FHIR Bearer Token generated via POST /oauth/token")));
    }
}
