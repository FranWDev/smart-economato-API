package com.economato.inventory.infrastructure.config.web;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Smart Economato")
                        .version("2.0")
                        .description(
                                "Documentación completa de todos los endpoints de la API de Smart Economato."));
    }
}
