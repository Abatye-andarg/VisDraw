package com.visualnotes.security;

import java.util.Map;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.visualnotes.controller.ApiProblems;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 600_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        String id = "pbkdf2-sha256-v1";
        return new DelegatingPasswordEncoder(id, Map.of(id, encoder));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiProblems problems) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .csrf(Customizer.withDefaults())
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(form -> form
                        .loginPage("/api/auth/login")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .successHandler((request, response, authentication) -> response.setStatus(204))
                        .failureHandler((request, response, exception) -> problems.write(response,
                                HttpStatus.UNAUTHORIZED, "Invalid email or password.")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> problems.write(response,
                                HttpStatus.UNAUTHORIZED, "Sign in to access this resource."))
                        .accessDeniedHandler((request, response, exception) -> problems.write(response,
                                HttpStatus.FORBIDDEN, "Request not permitted. Obtain a fresh CSRF token and try again.")))
                .build();
    }
}
