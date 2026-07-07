package com.quadteknologi.crm.config;

import com.quadteknologi.crm.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int REMEMBER_ME_SECONDS = 14 * 24 * 60 * 60;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserDetailsService userDetailsService) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/images/**",
                                "/VAADIN/**",
                                "/sw.js",
                                "/offline-stub.html",
                                "/manifest.webmanifest",
                                "/favicon.ico",
                                "/favicon.svg",
                                "/icons/**",
                                "/templates/**")
                        .permitAll())
                .rememberMe(rememberMe -> rememberMe
                        .key("quad-crm-remember-me")
                        .userDetailsService(userDetailsService)
                        .tokenValiditySeconds(REMEMBER_ME_SECONDS)
                        .alwaysRemember(true))
                .with(VaadinSecurityConfigurer.vaadin(), configurer -> {
                    configurer.loginView(LoginView.class);
                }).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
