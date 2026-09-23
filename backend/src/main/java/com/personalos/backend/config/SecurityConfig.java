package com.personalos.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personalos.backend.common.error.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String AUTH = "/api/v1/auth/";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // Sessions are cookie-based, so CSRF protection stays on. The client fetches the
            // token from GET /api/v1/auth/csrf and sends it in the X-XSRF-TOKEN header.
            .csrf(csrf -> csrf
                .csrfTokenRepository(new CookieCsrfTokenRepository())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/v1/health", "/actuator/health", AUTH + "csrf").permitAll()
                .requestMatchers(HttpMethod.POST,
                        AUTH + "register", AUTH + "verify-email", AUTH + "login",
                        AUTH + "forgot-password", AUTH + "reset-password").permitAll()
                .anyRequest().authenticated())
            .logout(logout -> logout
                .logoutUrl(AUTH + "logout")
                .logoutSuccessHandler((request, response, authentication) ->
                        response.setStatus(HttpStatus.NO_CONTENT.value()))
                .deleteCookies("SESSION"))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, e) ->
                        writeError(mapper, request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                                "Authentication required"))
                .accessDeniedHandler((request, response, e) -> {
                    boolean csrf = e instanceof CsrfException;
                    writeError(mapper, request, response, HttpStatus.FORBIDDEN,
                            csrf ? "CSRF_TOKEN_INVALID" : "FORBIDDEN",
                            csrf ? "Missing or invalid CSRF token" : "Access denied");
                }));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    private static void writeError(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
                                   HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(),
                new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI(), List.of()));
    }
}
