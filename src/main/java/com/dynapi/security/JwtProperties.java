package com.dynapi.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {
    /**
     * Base64-encoded HMAC secret used to validate Bearer tokens.
     * Replace this with a strong secret in non-local environments.
     */
    private String secret = "ZHluYXBpLWRldi1zZWNyZXQta2V5LWNoYW5nZS1tZS0xMjM0NTY3ODkw";
}
