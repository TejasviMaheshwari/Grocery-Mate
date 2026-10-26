
package com.grocerymate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
@Configuration
public class SecurityConfig {

    @Value("${cors.allowed.origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .cors(Customizer.withDefaults())

                    .csrf(csrf -> csrf
                            .csrfTokenRepository(
                                    CookieCsrfTokenRepository.withHttpOnlyFalse()
                            )
                            .csrfTokenRequestHandler(
                                    new CsrfTokenRequestAttributeHandler()
                            )
                    )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )


                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/style.css", "/app.js",
                                "/favicon.ico", "/error"
                        ).permitAll()

                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/csrf").permitAll()

                        .requestMatchers("/api/household/**").authenticated()
                        .requestMatchers("/api/vendor/**").hasRole("VENDOR")

                        .anyRequest().permitAll()
                );


        return http.build();
    }


    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList()
        );

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of(
                "Content-Type",
                "Accept",
                "X-XSRF-TOKEN"
        ));

        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }

    //    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration config = new CorsConfiguration();
//
//        // Use the Spring Boot-served frontend.
//        config.setAllowedOrigins(List.of(
//                "http://localhost:8080",
//                "http://127.0.0.1:8080"
//        ));
//
//        config.setAllowedMethods(List.of(
//                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
//        ));
//
//        config.setAllowedHeaders(List.of(
//                "Content-Type", "Accept", "X-XSRF-TOKEN"
//        ));
//
//        config.setAllowCredentials(true);
//
//        UrlBasedCorsConfigurationSource source =
//                new UrlBasedCorsConfigurationSource();
//
//        source.registerCorsConfiguration("/**", config);
//        return source;
//    }

}
