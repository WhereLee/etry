package com.wherelee.etry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wherelee.etry.auth.JwtAuthenticationFilter;
import com.wherelee.etry.common.ApiResponse;
import com.wherelee.etry.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/verify-email",
                                "/api/auth/verify-email/resend",
                                "/api/auth/login",
                                "/api/auth/password/forgot",
                                "/api/auth/password/reset").permitAll()
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        .requestMatchers("/local-storage/**", "/api/dev/**").permitAll()
                        .requestMatchers("/s/**").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/login", "/register", "/verify", "/forgot", "/reset", "/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(this::onUnauthenticated)
                        .accessDeniedHandler((request, response, e) ->
                                writeError(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 阻止 Spring Boot 把过滤器再注册到 servlet 容器，避免一个请求走两次 */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    private void onUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException e) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            writeError(response, ErrorCode.UNAUTHORIZED);
        } else {
            response.sendRedirect("/login");
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(HttpStatus.valueOf(errorCode.getStatus().value()).value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
