package com.twinlock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    // Accepts comma-separated origins, e.g.:
    // CORS_ORIGIN=https://twin-lock-event-web-app.vercel.app,https://twin-lock-event-web-app-git-main.vercel.app
    @Value("${twinlock.cors-origin:http://localhost:5173}")
    private String corsOrigin;

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                // Split on comma to support multiple origins
                String[] origins = corsOrigin.split(",");
                // Always include local dev origins
                String[] all = new String[origins.length + 2];
                System.arraycopy(origins, 0, all, 0, origins.length);
                all[origins.length] = "http://localhost:5173";
                all[origins.length + 1] = "http://127.0.0.1:5173";

                registry.addMapping("/api/**")
                        .allowedOrigins(all)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false);
            }
        };
    }
}
