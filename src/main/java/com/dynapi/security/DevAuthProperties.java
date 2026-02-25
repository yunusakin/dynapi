package com.dynapi.security;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dynapi.dev-auth")
public class DevAuthProperties {
    private boolean enabled = false;
    private String defaultSubject = "dev-admin";
    private List<String> defaultRoles = new ArrayList<>(List.of("ADMIN"));
    private long defaultTtlSeconds = 3600;
    private long maxTtlSeconds = 86400;
}
