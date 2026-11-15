package com.easybikerentals.config;

import com.easybikerentals.security.JwtFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    private static final String AUTH_FAILURE_ATTRIBUTE = "authFailure";

    @Value("${app.cors.origin}")
    private String origin;

    /**
     * JwtFilter is installed in Spring Security below. Disable Boot's automatic
     * servlet registration so the filter runs exactly once, inside that chain.
     */
    @Bean
    FilterRegistrationBean<JwtFilter> jwtServletRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    SecurityFilterChain filter(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration configuration = new CorsConfiguration();
                    configuration.setAllowedOrigins(List.of(
                            origin,
                            "http://localhost:5173",
                            "http://127.0.0.1:5173",
                            "http://localhost:5174",
                            "http://127.0.0.1:5174"));
                    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    configuration.setAllowedHeaders(List.of("*"));
                    return configuration;
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    String code = (String) request.getAttribute(AUTH_FAILURE_ATTRIBUTE);
                    String authorization = request.getHeader("Authorization");
                    if (code == null) {
                        code = authorization == null || !authorization.startsWith("Bearer ")
                                ? "MISSING_BEARER_TOKEN"
                                : "AUTH_REQUIRED";
                    }
                    log.warn("Authentication rejected {} {} with code={} bearerPresent={}",
                            request.getMethod(), request.getRequestURI(), code, authorization != null);
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write("{\"code\":\"" + code
                            + "\",\"message\":\"API session check: " + code
                            + ". Reload this site and log in again.\"}");
                }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**", "/api/bikes/**", "/api/contact", "/api/payments/webhook").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
