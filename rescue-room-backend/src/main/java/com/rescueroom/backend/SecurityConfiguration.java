package com.rescueroom.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Value("${RESCUE_ADMIN_USERNAME:}")
    private String adminUsername;

    @Value("${RESCUE_ADMIN_PASSWORD:}")
    private String adminPassword;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/admin/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin", "/admin/**", "/api/admin/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("FATAL: RESCUE_ADMIN_USERNAME and RESCUE_ADMIN_PASSWORD must be explicitly configured.");
        }
        UserDetails user = User.withDefaultPasswordEncoder()
                .username(adminUsername)
                .password(adminPassword)
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }
}
