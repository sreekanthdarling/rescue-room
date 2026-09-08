package com.rescueroom.backend.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@EnableConfigurationProperties(AdminConfiguration.Settings.class)
public class AdminConfiguration {
    @Validated
    @ConfigurationProperties("admin")
    public record Settings(@DefaultValue("30") @Min(1) @Max(86400) int inactivitySeconds) { }
}
