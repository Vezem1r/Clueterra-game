package io.clueterra.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Clueterra API")
                                .version("1.0.0")
                                .description(
                                        "API for Clueterra - a geography guessing game. Players try"
                                                + " to guess countries based on hints about geography,"
                                                + " culture, economy, and more.")
                                .contact(new Contact().name("Clueterra Team").email("support@clueterra.com"))
                                .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("/").description("Current server"))
                .addTagsItem(new Tag().name("Auth").description("Authentication and registration"))
                .addTagsItem(new Tag().name("Game").description("Game session management and gameplay"))
                .addTagsItem(new Tag().name("Profile").description("User profiles and leaderboard"))
                .addTagsItem(new Tag().name("Reports").description("Hint reporting for registered users"))
                .addTagsItem(
                        new Tag().name("Admin - Countries").description("Country management (Admin only)"))
                .addTagsItem(new Tag().name("Admin - Hints").description("Hint management (Admin only)"))
                .addTagsItem(
                        new Tag()
                                .name("Admin - Statistics")
                                .description("Country statistics management (Admin only)"))
                .addTagsItem(
                        new Tag().name("Admin - Challenges").description("Challenge management (Admin only)"))
                .addTagsItem(
                        new Tag().name("Admin - Reports").description("Hint report management (Admin only)"))
                .addTagsItem(new Tag().name("Admin - Logs").description("System logs (Admin only)"))
                .addTagsItem(
                        new Tag()
                                .name("Admin - Analytics")
                                .description("Analytics and data export (Admin only)"))
                .addTagsItem(
                        new Tag()
                                .name("Admin - Sync")
                                .description("Data sync with questions service (Admin only)"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "JWT token obtained from /api/auth/login or" + " /api/auth/register")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
