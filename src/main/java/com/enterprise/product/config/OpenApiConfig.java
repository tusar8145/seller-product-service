package com.enterprise.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI productServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Seller Product Service API")
                .description("Enterprise-grade Seller Product Service with Redis-first reads and high-traffic stock updates")
                .version("v1.0.0"));
    }
}
