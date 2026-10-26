package com.grocerymate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Enable CORS using corsConfigurationSource() bean
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 2. Disable CSRF for decoupled REST APIs
            .csrf(csrf -> csrf.disable())

            // 3. Session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // 4. Endpoint authorization
            .authorizeHttpRequests(auth -> auth
                // Explicitly allow all HTTP OPTIONS preflight requests
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Static resources & Auth endpoints
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/user-dashboard.html",
                    "/vendor-dashboard.html",
                    "/*.css",
                    "/*.js",
                    "/*.png",
                    "/*.ico",
                    "/error",
                    "/api/csrf",
                    "/api/auth/**"
                ).permitAll()

                // Dashboard API access
                .requestMatchers("/api/household/**", "/api/vendor/**").permitAll()

                .anyRequest().permitAll()
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Support local development, custom Vercel domains, and all Vercel preview deployments
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*",
            "https://*.vercel.app",
            "https://grocery-mate-eight.vercel.app",
            "https://grocery-mate-tej24asvi-7046.vercel.app"
        ));

        config.setAllowedMethods(List.of(
            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
            "Content-Type",
            "Accept",
            "Authorization",
            "X-Requested-With",
            "X-XSRF-TOKEN"
        ));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}